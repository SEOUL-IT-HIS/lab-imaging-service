package kr.co.seoulit.his.labimagingservice.laborder.messaging;

import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.exception.OrderNotYetReceivedException;
import kr.co.seoulit.his.labimagingservice.common.status.CancelOutcome;
import kr.co.seoulit.his.labimagingservice.interfacelog.entity.InterfaceOrderType;
import kr.co.seoulit.his.labimagingservice.interfacelog.entity.InterfaceReceiveLogEntity;
import kr.co.seoulit.his.labimagingservice.interfacelog.service.InterfaceReceiveLogService;
import kr.co.seoulit.his.labimagingservice.laborder.dto.ItemCancelResult;
import kr.co.seoulit.his.labimagingservice.laborder.dto.LabOrderCancelResultDto;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabOrderEntity;
import kr.co.seoulit.his.labimagingservice.laborder.messaging.dto.EventEnvelope;
import kr.co.seoulit.his.labimagingservice.laborder.messaging.dto.LabOrderCancelledData;
import kr.co.seoulit.his.labimagingservice.laborder.repository.LabOrderRepository;
import kr.co.seoulit.his.labimagingservice.laborder.service.LabOrderCancelCommand;
import kr.co.seoulit.his.labimagingservice.laborder.service.LabOrderCancelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 처방 취소 수신 Consumer — 멱등(dedup/RECEIVED 재사용)·예외 전파·모르는 필드 허용. (05번 지시서 Phase 6)
 *
 * ⚠ LabOrderRequestedConsumerTest 가 없어 참고할 기존 패턴이 없다 — LabOrderIntakeServiceTest/
 *   LabOrderControllerTest 의 "실제 ObjectMapper + mock 의존성, 메서드 직접 호출" 스타일을 따른다.
 */
class LabOrderCancelledConsumerTest {

    private static final String EVENT_ID = "evt-1";
    private static final String PRESCRIPTION_ID = "RX-1";

    private final LabOrderCancelService labOrderCancelService = mock(LabOrderCancelService.class);
    private final LabOrderRepository labOrderRepository = mock(LabOrderRepository.class);
    private final InterfaceReceiveLogService interfaceReceiveLogService = mock(InterfaceReceiveLogService.class);
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private final LabOrderCancelledConsumer consumer = new LabOrderCancelledConsumer(
            labOrderCancelService, labOrderRepository, interfaceReceiveLogService, objectMapper);

    @BeforeEach
    void setUp() {
        when(interfaceReceiveLogService.logReceived(any(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn("log-1");
    }

    private EventEnvelope<LabOrderCancelledData> envelope(Object data) {
        return new EventEnvelope<>(EVENT_ID, "LabOrderCancelled", "1.0",
                OffsetDateTime.now(), "01", null, (LabOrderCancelledData) data);
    }

    private LabOrderCancelledData.Item item(String itemCode) {
        return new LabOrderCancelledData.Item(itemCode, "일반혈액검사", "LO-1");
    }

    private LabOrderCancelledData data() {
        return new LabOrderCancelledData(PRESCRIPTION_ID, "오처방", "DOC-1", List.of(item("CBC")));
    }

    private LabOrderCancelResultDto successResult(CancelOutcome outcome) {
        return LabOrderCancelResultDto.of(PRESCRIPTION_ID, outcome,
                List.of(LabOrderCancelResultDto.ItemResult.of("CBC", ItemCancelResult.CANCELLED, "취소되었습니다.")));
    }

    @Test
    @DisplayName("이미 처리된 이벤트(결과코드 확정)는 재처리하지 않고 조용히 종료한다")
    void duplicateFinishedEventIsIgnored() {
        // ⚠ mock 을 쓴다(실제 빌더 생성 엔티티가 아니라) — getInterfaceReceiveLogId() 는 @PrePersist 로만
        //   채워지는데 단위 테스트에는 JPA 생명주기가 없어 null 로 남는다. Optional.map 은 매퍼 결과가
        //   null 이면 Optional.empty() 로 접히므로, 실제 엔티티를 쓰면 "로그 재사용" 분기가 깨진다.
        InterfaceReceiveLogEntity finished = mock(InterfaceReceiveLogEntity.class);
        when(finished.getResultCode()).thenReturn(LabMessageCode.LAB118);
        when(interfaceReceiveLogService.findByEventId(EVENT_ID)).thenReturn(Optional.of(finished));

        consumer.onLabOrderCancelled(envelope(data()));

        verify(labOrderCancelService, never()).cancel(any());
        verify(interfaceReceiveLogService, never()).logReceived(any(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("앞 시도가 RECEIVED(처리 중) 상태로 남아 있으면 그 로그를 재사용하고 다시 처리한다")
    void receivedStatePreviousLogIsReused() {
        InterfaceReceiveLogEntity inProgress = mock(InterfaceReceiveLogEntity.class);
        when(inProgress.getResultCode()).thenReturn(InterfaceReceiveLogService.RESULT_RECEIVED);
        when(inProgress.getInterfaceReceiveLogId()).thenReturn("log-reused-1");
        when(interfaceReceiveLogService.findByEventId(EVENT_ID)).thenReturn(Optional.of(inProgress));
        when(labOrderRepository.findByLabOrderNo(PRESCRIPTION_ID)).thenReturn(Optional.empty());
        when(labOrderCancelService.cancel(any())).thenReturn(successResult(CancelOutcome.CANCELLED));

        consumer.onLabOrderCancelled(envelope(data()));

        // 새 로그를 만들지 않고(logReceived 호출 없음) 기존 로그ID로 결과만 기록한다.
        verify(interfaceReceiveLogService, never()).logReceived(any(), anyString(), anyString(), anyString(), anyString());
        verify(interfaceReceiveLogService).markResult(eq("log-reused-1"), eq(LabMessageCode.LAB118), anyString());
        verify(labOrderCancelService).cancel(any());
    }

    @Test
    @DisplayName("처음 보는 이벤트 — 오더를 찾으면 그 오더의 systemCode 로 수신 기록을 남긴다")
    void firstTimeEventLogsWithOrderSystemCode() {
        when(interfaceReceiveLogService.findByEventId(EVENT_ID)).thenReturn(Optional.empty());
        LabOrderEntity order = LabOrderEntity.builder()
                .labOrderNo(PRESCRIPTION_ID).systemCode("02").treatTypeCode("01")
                .urgencyYn("N").orderStatusCode("RECEIVED").receivedAt(LocalDateTime.now()).build();
        when(labOrderRepository.findByLabOrderNo(PRESCRIPTION_ID)).thenReturn(Optional.of(order));
        when(labOrderCancelService.cancel(any())).thenReturn(successResult(CancelOutcome.CANCELLED));

        consumer.onLabOrderCancelled(envelope(data()));

        verify(interfaceReceiveLogService).logReceived(
                eq(InterfaceOrderType.LAB), eq("02"), anyString(), eq(EVENT_ID), eq("LabOrderCancelled"));
    }

    @Test
    @DisplayName("처음 보는 이벤트 — 오더를 못 찾으면 봉투의 source 로 수신 기록을 남긴다")
    void firstTimeEventFallsBackToEnvelopeSourceWhenOrderNotFound() {
        when(interfaceReceiveLogService.findByEventId(EVENT_ID)).thenReturn(Optional.empty());
        when(labOrderRepository.findByLabOrderNo(PRESCRIPTION_ID)).thenReturn(Optional.empty());
        when(labOrderCancelService.cancel(any())).thenReturn(successResult(CancelOutcome.CANCELLED));

        consumer.onLabOrderCancelled(envelope(data()));

        // 봉투(envelope)의 source="01" 을 그대로 쓴다 — Consumer 자체가 오더를 못 찾았을 때의 폴백이다.
        verify(interfaceReceiveLogService).logReceived(
                eq(InterfaceOrderType.LAB), eq("01"), anyString(), eq(EVENT_ID), eq("LabOrderCancelled"));
    }

    @Test
    @DisplayName("OrderNotYetReceivedException 은 잡지 않고 다시 던진다 — 수신 기록은 RECEIVED 로 남는다(재시도 대상)")
    void orderNotYetReceivedPropagatesAndLeavesLogReceived() {
        when(interfaceReceiveLogService.findByEventId(EVENT_ID)).thenReturn(Optional.empty());
        when(labOrderRepository.findByLabOrderNo(PRESCRIPTION_ID)).thenReturn(Optional.empty());
        when(labOrderCancelService.cancel(any())).thenThrow(new OrderNotYetReceivedException("아직 접수되지 않았습니다."));

        assertThatThrownBy(() -> consumer.onLabOrderCancelled(envelope(data())))
                .isInstanceOf(OrderNotYetReceivedException.class);

        // 결과를 기록하지 않는다 — 로그는 RECEIVED 로 남아야 한다("끝내 처리되지 못했다"를 정확히 나타냄).
        verify(interfaceReceiveLogService, never()).markResult(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("전부 취소/일부 거절/전부 거절에 따라 결과코드 LAB118/119/120 을 각각 기록한다")
    void recordsResultCodePerOutcome() {
        when(interfaceReceiveLogService.findByEventId(EVENT_ID)).thenReturn(Optional.empty());
        when(labOrderRepository.findByLabOrderNo(PRESCRIPTION_ID)).thenReturn(Optional.empty());

        when(labOrderCancelService.cancel(any())).thenReturn(successResult(CancelOutcome.CANCELLED));
        consumer.onLabOrderCancelled(envelope(data()));
        verify(interfaceReceiveLogService).markResult(anyString(), eq(LabMessageCode.LAB118), anyString());

        when(labOrderCancelService.cancel(any())).thenReturn(successResult(CancelOutcome.PARTIAL));
        consumer.onLabOrderCancelled(envelope(data()));
        verify(interfaceReceiveLogService).markResult(anyString(), eq(LabMessageCode.LAB119), anyString());

        when(labOrderCancelService.cancel(any())).thenReturn(successResult(CancelOutcome.REFUSED));
        consumer.onLabOrderCancelled(envelope(data()));
        verify(interfaceReceiveLogService).markResult(anyString(), eq(LabMessageCode.LAB120), anyString());
    }

    @Test
    @DisplayName("모르는 필드가 섞여 와도(@JsonIgnoreProperties) 변환이 깨지지 않는다 — type erasure 로 Map 으로 들어오는 경로")
    void unknownFieldsAreIgnoredWhenDataArrivesAsMap() {
        when(interfaceReceiveLogService.findByEventId(EVENT_ID)).thenReturn(Optional.empty());
        when(labOrderRepository.findByLabOrderNo(PRESCRIPTION_ID)).thenReturn(Optional.empty());
        when(labOrderCancelService.cancel(any())).thenReturn(successResult(CancelOutcome.CANCELLED));

        // Kafka 역직렬화 시 EventEnvelope<T> 의 T 가 지워져 data 가 Map 으로 들어온다(toCancelledData 참고).
        Map<String, Object> rawData = new LinkedHashMap<>();
        rawData.put("prescriptionId", PRESCRIPTION_ID);
        rawData.put("cancelReason", "오처방");
        rawData.put("cancelledBy", "DOC-1");
        rawData.put("cancelledItems", List.of(Map.of("itemCode", "CBC", "itemName", "일반혈액검사")));
        rawData.put("futureFieldWeDoNotKnowYet", "무시해야 한다");

        EventEnvelope<LabOrderCancelledData> raw = new EventEnvelope<>(
                EVENT_ID, "LabOrderCancelled", "1.0", OffsetDateTime.now(), "01", null, null);
        // EventEnvelope.data 는 raw Object 로 세팅해(역직렬화 흉내) Consumer 가 objectMapper.convertValue 를 타게 한다.
        EventEnvelope<Object> envelopeWithMapData = new EventEnvelope<>(
                raw.getEventId(), raw.getEventType(), raw.getVersion(), raw.getOccurredAt(), raw.getSource(),
                raw.getCorrelationId(), rawData);

        @SuppressWarnings({"unchecked", "rawtypes"})
        EventEnvelope<LabOrderCancelledData> casted = (EventEnvelope) envelopeWithMapData;

        consumer.onLabOrderCancelled(casted);

        ArgumentCaptor<LabOrderCancelCommand> captor = ArgumentCaptor.forClass(LabOrderCancelCommand.class);
        verify(labOrderCancelService).cancel(captor.capture());
        assertThat(captor.getValue().prescriptionId()).isEqualTo(PRESCRIPTION_ID);
        assertThat(captor.getValue().items()).extracting(LabOrderCancelCommand.Item::itemCode).containsExactly("CBC");
    }
}
