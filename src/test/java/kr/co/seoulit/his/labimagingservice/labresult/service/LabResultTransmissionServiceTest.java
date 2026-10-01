package kr.co.seoulit.his.labimagingservice.labresult.service;

import kr.co.seoulit.his.labimagingservice.interfacelog.send.SendEventType;
import kr.co.seoulit.his.labimagingservice.interfacelog.send.service.InterfaceSendLogService;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabOrderEntity;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabOrderItemEntity;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabReceptionEntity;
import kr.co.seoulit.his.labimagingservice.laborder.messaging.dto.EventEnvelope;
import kr.co.seoulit.his.labimagingservice.laborder.repository.LabOrderItemRepository;
import kr.co.seoulit.his.labimagingservice.laborder.repository.LabReceptionRepository;
import kr.co.seoulit.his.labimagingservice.labresult.entity.LabResultDetailEntity;
import kr.co.seoulit.his.labimagingservice.labresult.entity.LabResultEntity;
import kr.co.seoulit.his.labimagingservice.labresult.messaging.dto.LabResultReportedData;
import kr.co.seoulit.his.labimagingservice.labresult.microbiology.repository.MicrobiologyResultRepository;
import kr.co.seoulit.his.labimagingservice.labresult.pathology.repository.PathologyResultRepository;
import kr.co.seoulit.his.labimagingservice.labresult.repository.LabResultRepository;
import kr.co.seoulit.his.labimagingservice.labresult.type.LabResultType;
import kr.co.seoulit.his.labimagingservice.labresult.type.LabResultTypeResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 검사결과 전송 (5차 Phase 6, D9/D10).
 */
class LabResultTransmissionServiceTest {

    private final InterfaceSendLogService sendLogService = mock(InterfaceSendLogService.class);
    private final LabReceptionRepository receptionRepository = mock(LabReceptionRepository.class);
    private final LabOrderItemRepository itemRepository = mock(LabOrderItemRepository.class);
    private final LabResultRepository labResultRepository = mock(LabResultRepository.class);
    private final MicrobiologyResultRepository microRepository = mock(MicrobiologyResultRepository.class);
    private final PathologyResultRepository pathologyRepository = mock(PathologyResultRepository.class);

    private final LabResultTransmissionService service = new LabResultTransmissionService(sendLogService,
            receptionRepository, itemRepository, labResultRepository, microRepository, pathologyRepository,
            new LabResultTypeResolver(Map.of("07", LabResultType.PATHOLOGY)));

    private LabResultEntity result;

    @BeforeEach
    void setUp() {
        LabOrderEntity order = mock(LabOrderEntity.class);
        when(order.getLabOrderId()).thenReturn("order-1");
        when(order.getLabOrderNo()).thenReturn("RX-1");
        when(order.getPatientId()).thenReturn("p-1");
        when(order.getSystemCode()).thenReturn("01");
        when(order.getUrgencyYn()).thenReturn("Y");

        // 오더 항목 2개: 일반(01, 이번에 확정) + 병리(07, 미확정) → 진행도 1/2
        LabOrderItemEntity general = item(order, "item-1", "01");
        LabOrderItemEntity pathology = item(order, "item-7", "07");
        when(itemRepository.findByLabOrderIdIn(List.of("order-1"))).thenReturn(List.of(general, pathology));

        result = mock(LabResultEntity.class);
        when(result.getLabResultId()).thenReturn("res-1");
        when(result.getLabOrderItem()).thenReturn(general);
        when(result.getResultValue()).thenReturn("5.2");
        when(result.getReferenceRange()).thenReturn("3.5-6.0");
        when(result.getAbnormalYn()).thenReturn("N");
        when(result.getResultStatusCode()).thenReturn("02");
        when(result.getConfirmedById()).thenReturn("emp-2");
        when(result.getConfirmedAt()).thenReturn(LocalDateTime.of(2026, 9, 29, 10, 0));
        when(labResultRepository.findByLabOrderItem_LabOrderItemIdIn(anyList())).thenReturn(List.of(result));
        when(pathologyRepository.findByItemIds(anyList())).thenReturn(List.of());

        LabReceptionEntity reception = mock(LabReceptionEntity.class);
        when(reception.getLabReceptionId()).thenReturn("rec-1");
        when(reception.getReceptionNo()).thenReturn("LR-1");
        when(receptionRepository.findByLabOrder_LabOrderIdAndReceptionStatusCodeOrderByCreatedAtDesc(eq("order-1"), anyString()))
                .thenReturn(List.of(reception));
        when(microRepository.findByReceptionIds(anyList())).thenReturn(List.of());
    }

    private static LabOrderItemEntity item(LabOrderEntity order, String id, String code) {
        LabOrderItemEntity item = mock(LabOrderItemEntity.class);
        when(item.getLabOrder()).thenReturn(order);
        when(item.getLabOrderItemId()).thenReturn(id);
        when(item.getLabItemCode()).thenReturn(code);
        return item;
    }

    @Test
    @DisplayName("일반결과 확정 → RESULT(01) 이력. 처방코어 제안 형식: LabResultReported, correlationId=labOrderId, items[1건]")
    @SuppressWarnings("unchecked")
    void generalEnvelope() {
        service.transmitGeneral(result);

        ArgumentCaptor<Function<String, Object>> factory = ArgumentCaptor.forClass(Function.class);
        verify(sendLogService).recordPending(eq(SendEventType.RESULT), eq("res-1"), eq("01"), factory.capture());

        EventEnvelope<LabResultReportedData> envelope =
                (EventEnvelope<LabResultReportedData>) factory.getValue().apply("evt-1");
        assertThat(envelope.getEventId()).isEqualTo("evt-1");
        assertThat(envelope.getEventType()).isEqualTo("LabResultReported");
        assertThat(envelope.getVersion()).isEqualTo("1.0");
        assertThat(envelope.getSource()).isEqualTo("LAB");
        assertThat(envelope.getCorrelationId()).isEqualTo("order-1");
        assertThat(envelope.getOccurredAt().getOffset().getTotalSeconds()).isEqualTo(9 * 3600);

        LabResultReportedData data = envelope.getData();
        assertThat(data.getPrescriptionId()).isEqualTo("RX-1");
        assertThat(data.getLabOrderId()).isEqualTo("order-1");
        assertThat(data.getResultStatus()).isEqualTo("FINAL");
        assertThat(data.getReportedAt().getOffset().getTotalSeconds()).isEqualTo(9 * 3600);
        assertThat(data.getReceptionNo()).isEqualTo("LR-1");
        assertThat(data.getUrgencyYn()).isEqualTo("Y");
        assertThat(data.getConfirmedItemCount()).isEqualTo(1);
        assertThat(data.getTotalItemCount()).isEqualTo(2);

        assertThat(data.getItems()).hasSize(1);
        LabResultReportedData.Item item = data.getItems().get(0);
        assertThat(item.getItemCode()).isEqualTo("01");
        assertThat(item.getItemName()).isNull();          // 스냅샷 금지 — 수신측이 공통코드로 풀어 쓴다
        assertThat(item.getResultValue()).isEqualTo("5.2");
        assertThat(item.getAbnormalFlag()).isEqualTo("N"); // 5.2 는 3.5-6.0 범위 안 → 정상
        assertThat(item.getResultType()).isEqualTo("GENERAL");
        assertThat(item.getDetails()).isNull(); // 결과항목 없는 검사(기존 방식) — details 는 안 채운다
    }

    @Test
    @DisplayName("6차: 결과항목(상세)이 있는 검사는 상위 resultValue/unit/referenceRange/abnormalFlag 가 전부 null 이고 items[].details[] 가 채워진다")
    @SuppressWarnings("unchecked")
    void detailModeFillsDetailsAndNullsHeader() {
        LabResultDetailEntity d1 = detail(1, "H01", "4.5", "10^6/uL", "4.0-5.5", "N");
        LabResultDetailEntity d2 = detail(2, "H02", "20.0", "g/dL", "13.0-17.0", "Y");
        when(result.getDetails()).thenReturn(List.of(d1, d2));

        service.transmitGeneral(result);

        ArgumentCaptor<Function<String, Object>> factory = ArgumentCaptor.forClass(Function.class);
        verify(sendLogService).recordPending(eq(SendEventType.RESULT), eq("res-1"), eq("01"), factory.capture());
        EventEnvelope<LabResultReportedData> envelope =
                (EventEnvelope<LabResultReportedData>) factory.getValue().apply("evt-1");

        LabResultReportedData.Item item = envelope.getData().getItems().get(0);
        assertThat(item.getResultValue()).isNull();
        assertThat(item.getUnit()).isNull();
        assertThat(item.getReferenceRange()).isNull();
        assertThat(item.getAbnormalFlag()).isNull();

        assertThat(item.getDetails()).hasSize(2);
        LabResultReportedData.Detail reportedD1 = item.getDetails().get(0);
        assertThat(reportedD1.getResultItemCode()).isEqualTo("H01");
        assertThat(reportedD1.getResultValue()).isEqualTo("4.5");
        assertThat(reportedD1.getUnit()).isEqualTo("10^6/uL");
        assertThat(reportedD1.getReferenceRange()).isEqualTo("4.0-5.5");
        assertThat(reportedD1.getAbnormalFlag()).isEqualTo("N");

        LabResultReportedData.Detail reportedD2 = item.getDetails().get(1);
        assertThat(reportedD2.getResultItemCode()).isEqualTo("H02");
        assertThat(reportedD2.getResultValue()).isEqualTo("20.0");
        assertThat(reportedD2.getAbnormalFlag()).isEqualTo("H"); // 20.0 은 13.0-17.0 상한 초과 → 높음
    }

    private static LabResultDetailEntity detail(int seq, String resultItemCode, String resultValue,
                                                 String resultUnit, String referenceRange, String abnormalYn) {
        LabResultDetailEntity detail = mock(LabResultDetailEntity.class);
        when(detail.getDetailSeq()).thenReturn(seq);
        when(detail.getResultItemCode()).thenReturn(resultItemCode);
        when(detail.getResultValue()).thenReturn(resultValue);
        when(detail.getResultUnit()).thenReturn(resultUnit);
        when(detail.getReferenceRange()).thenReturn(referenceRange);
        when(detail.getAbnormalYn()).thenReturn(abnormalYn);
        return detail;
    }

    @Test
    @DisplayName("abnormalFlag 방향 판정(2026-09-30 처방코어 회신 반영): N=정상/H=상한초과/L=하한미만/null=판정불가")
    void abnormalFlagDirection() {
        assertThat(AbnormalYnDecider.decideDirection("5.0", "3.5-6.0")).isEqualTo("N");
        assertThat(AbnormalYnDecider.decideDirection("7.0", "3.5-6.0")).isEqualTo("H");
        assertThat(AbnormalYnDecider.decideDirection("2.0", "3.5-6.0")).isEqualTo("L");
        assertThat(AbnormalYnDecider.decideDirection("4.2", null)).isNull();       // 참고범위 없음 → 판정 불가
        assertThat(AbnormalYnDecider.decideDirection("양성", "음성,정상")).isNull(); // 정성 비정상 — 방향 없음
        assertThat(AbnormalYnDecider.decideDirection("정상", "음성,정상")).isEqualTo("N"); // 정성 정상
    }

    @Test
    @DisplayName("이력 기록이 실패해도 예외를 던지지 않는다 (확정은 성공해야 한다)")
    void neverThrows() {
        when(sendLogService.recordPending(any(), anyString(), anyString(), any()))
                .thenThrow(new IllegalStateException("no tx"));

        service.transmitGeneral(result);
    }
}
