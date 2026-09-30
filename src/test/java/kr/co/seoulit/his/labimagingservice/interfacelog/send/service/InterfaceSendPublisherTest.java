package kr.co.seoulit.his.labimagingservice.interfacelog.send.service;

import kr.co.seoulit.his.labimagingservice.billing.messaging.dto.BillingChargeRequestData;
import kr.co.seoulit.his.labimagingservice.interfacelog.send.entity.InterfaceSendLogEntity;
import kr.co.seoulit.his.labimagingservice.interfacelog.send.repository.InterfaceSendLogRepository;
import kr.co.seoulit.his.labimagingservice.laborder.messaging.dto.EventEnvelope;
import kr.co.seoulit.his.labimagingservice.labresult.messaging.dto.LabResultReportedData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 발신 이력 → Kafka 발행 (5차 Phase 5, D8).
 * ⚠ 재발행해도 같은 원문·같은 키로 나가야 한다(수신측 멱등). 청구는 기존 규격(평문 BillingChargeRequestData,
 *   키=receptionId) 그대로여야 한다.
 */
@SuppressWarnings("unchecked")
class InterfaceSendPublisherTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
    private final InterfaceSendLogRepository repository = mock(InterfaceSendLogRepository.class);
    private final InterfaceSendStatusUpdater updater = mock(InterfaceSendStatusUpdater.class);
    private InterfaceSendPublisher publisher;

    private final BillingChargeRequestData billing = BillingChargeRequestData.builder()
            .patientId("p-1").receptionId("rec-1").sourceServiceCode("src").sourceRecordId("item-1")
            .feeCode("FEE002").itemName("01").quantity("1").build();

    @BeforeEach
    void setUp() {
        publisher = new InterfaceSendPublisher(kafkaTemplate, repository, updater, objectMapper,
                "exam-billing-charge", "lab.lab-result.reported.v1");
    }

    private InterfaceSendLogEntity billingLog(BillingChargeRequestData data) {
        InterfaceSendLogEntity log = InterfaceSendLogEntity.builder()
                .eventTypeCode("02").eventId("evt-1").referenceId("item-1").systemCode("06")
                .payload(objectMapper.writeValueAsString(data)).sendStatusCode("01").build();
        when(repository.findById("log-1")).thenReturn(Optional.of(log));
        return log;
    }

    @Test
    @DisplayName("청구 원문은 Jackson 3 왕복 후에도 필드가 그대로다 (기존 청구 규격 유지)")
    void billingPayloadRoundTrip() {
        String json = objectMapper.writeValueAsString(billing);
        BillingChargeRequestData back = objectMapper.readValue(json, BillingChargeRequestData.class);

        assertThat(objectMapper.writeValueAsString(back)).isEqualTo(json);
        assertThat(back.getReceptionId()).isEqualTo("rec-1");
        assertThat(back.getFeeCode()).isEqualTo("FEE002");
    }

    @Test
    @DisplayName("청구는 exam-billing-charge 토픽, 키=receptionId, 값=BillingChargeRequestData 로 보낸다")
    void billingTarget() {
        InterfaceSendPublisher.Target target = publisher.resolveTarget(billingLog(billing));

        assertThat(target.topic()).isEqualTo("exam-billing-charge");
        assertThat(target.key()).isEqualTo("rec-1");
        assertThat(target.payload()).isInstanceOf(BillingChargeRequestData.class);
    }

    @Test
    @DisplayName("발행 성공 콜백 → 02(완료)로 갱신")
    void successMarksSent() {
        billingLog(billing);
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        publisher.publish("log-1");

        verify(updater).markSent("log-1");
        verify(updater, never()).markFailed(anyString(), any());
    }

    @Test
    @DisplayName("발행 실패 콜백 → 03(실패) + 오류 메시지")
    void failureMarksFailed() {
        billingLog(billing);
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        publisher.publish("log-1");

        verify(updater).markFailed(eq("log-1"), eq("broker down"));
    }

    @Test
    @DisplayName("수가코드 없는 청구 원문은 보내지 않고 03 으로 남긴다")
    void noFeeCodeNotSent() {
        billingLog(BillingChargeRequestData.builder().receptionId("rec-1").sourceRecordId("item-1").build());

        publisher.publish("log-1");

        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
        verify(updater).markFailed(eq("log-1"), anyString());
    }

    @Test
    @DisplayName("결과 원문(EventEnvelope)은 Jackson 3 왕복 후 결과 보고 토픽, 키=prescriptionId, 같은 eventId 로 나간다 (Phase 6)")
    void resultTargetRoundTrip() {
        LabResultReportedData data = LabResultReportedData.builder()
                .prescriptionId("RX-1").labOrderId("order-1").resultStatus("FINAL")
                .reportedAt(OffsetDateTime.of(2026, 9, 29, 10, 0, 0, 0, ZoneOffset.ofHours(9)))
                .items(List.of(LabResultReportedData.Item.builder()
                        .itemCode("05").resultType("MICROBIOLOGY").resultId("res-1")
                        .susceptibilities(List.of(LabResultReportedData.Susceptibility.builder()
                                .antibioticCode("01").susceptibilityResultCode("01").build()))
                        .build()))
                .confirmedItemCount(1).totalItemCount(2).build();
        EventEnvelope<LabResultReportedData> envelope = new EventEnvelope<>("evt-9", "LabResultReported", "1.0",
                OffsetDateTime.now(ZoneOffset.ofHours(9)), "LAB", "order-1", data);
        InterfaceSendLogEntity log = InterfaceSendLogEntity.builder()
                .eventTypeCode("01").eventId("evt-9").referenceId("res-1").systemCode("01")
                .payload(objectMapper.writeValueAsString(envelope)).sendStatusCode("01").build();

        InterfaceSendPublisher.Target target = publisher.resolveTarget(log);

        assertThat(target.topic()).isEqualTo("lab.lab-result.reported.v1");
        assertThat(target.key()).isEqualTo("RX-1");
        EventEnvelope<?> sent = (EventEnvelope<?>) target.payload();
        assertThat(sent.getEventId()).isEqualTo("evt-9");
        assertThat(sent.getCorrelationId()).isEqualTo("order-1");
        // 다시 직렬화해도 본문이 그대로다 (재발행 시 같은 원문)
        assertThat(objectMapper.readTree(objectMapper.writeValueAsString(sent)))
                .isEqualTo(objectMapper.readTree(log.getPayload()));
    }

    @Test
    @DisplayName("재발행(publish 두 번)해도 이력의 event_id 는 그대로다")
    void republishKeepsEventId() {
        InterfaceSendLogEntity log = billingLog(billing);
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("x")));

        publisher.publish("log-1");
        publisher.publish("log-1");

        assertThat(log.getEventId()).isEqualTo("evt-1");
    }
}
