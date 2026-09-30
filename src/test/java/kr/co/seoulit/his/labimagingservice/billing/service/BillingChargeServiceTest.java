package kr.co.seoulit.his.labimagingservice.billing.service;

import kr.co.seoulit.his.labimagingservice.billing.messaging.dto.BillingChargeRequestData;
import kr.co.seoulit.his.labimagingservice.common.cache.CommonCodeCache;
import kr.co.seoulit.his.labimagingservice.imagingorder.entity.ImageOrderEntity;
import kr.co.seoulit.his.labimagingservice.imagingorder.entity.ImageOrderItemEntity;
import kr.co.seoulit.his.labimagingservice.interfacelog.send.SendEventType;
import kr.co.seoulit.his.labimagingservice.interfacelog.send.service.InterfaceSendLogService;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabOrderEntity;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabOrderItemEntity;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabReceptionEntity;
import kr.co.seoulit.his.labimagingservice.laborder.repository.LabReceptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 청구 요청 (5차 Phase 5, 후속조치 #3·#8).
 */
class BillingChargeServiceTest {

    private final InterfaceSendLogService sendLogService = mock(InterfaceSendLogService.class);
    private final LabReceptionRepository receptionRepository = mock(LabReceptionRepository.class);
    private final FeeCodeResolver feeCodeResolver = new FeeCodeResolver(Map.of("01", "FEE002"), Map.of());
    private final BillingChargeService service =
            new BillingChargeService(feeCodeResolver, sendLogService, receptionRepository, "src-uuid", "06");

    private LabOrderItemEntity item;

    @BeforeEach
    void setUp() {
        LabOrderEntity order = mock(LabOrderEntity.class);
        when(order.getLabOrderId()).thenReturn("order-1");
        when(order.getPatientId()).thenReturn("p-1");
        item = mock(LabOrderItemEntity.class);
        when(item.getLabOrder()).thenReturn(order);
        when(item.getLabOrderItemId()).thenReturn("item-1");

        LabReceptionEntity reception = mock(LabReceptionEntity.class);
        when(reception.getLabReceptionId()).thenReturn("rec-1");
        when(receptionRepository.findByLabOrder_LabOrderIdAndReceptionStatusCodeOrderByCreatedAtDesc(eq("order-1"), anyString()))
                .thenReturn(List.of(reception));
    }

    @Test
    @DisplayName("수가코드가 있으면 01(발행 예약) — 기존 규격 그대로(평문, 수량 1, 금액 null)")
    @SuppressWarnings("unchecked")
    void mappedCodeIsPending() {
        when(item.getLabItemCode()).thenReturn("01");

        service.requestLabCharge(item);

        ArgumentCaptor<Function<String, Object>> factory = ArgumentCaptor.forClass(Function.class);
        verify(sendLogService).recordPending(eq(SendEventType.BILLING), eq("item-1"), eq("06"), factory.capture());
        BillingChargeRequestData data = (BillingChargeRequestData) factory.getValue().apply("evt");
        assertThat(data.getReceptionId()).isEqualTo("rec-1");
        assertThat(data.getFeeCode()).isEqualTo("FEE002");
        assertThat(data.getQuantity()).isEqualTo("1");
        assertThat(data.getAmount()).isNull();
        assertThat(data.getSourceServiceCode()).isEqualTo("src-uuid");
    }

    @Test
    @DisplayName("수가코드가 없으면 03 + '수가코드 매핑 없음: {코드}' — 조회 화면에 누락이 드러난다")
    void unmappedCodeIsFailed() {
        when(item.getLabItemCode()).thenReturn("09");

        service.requestLabCharge(item);

        verify(sendLogService).recordFailed(eq(SendEventType.BILLING), eq("item-1"), eq("06"), any(), eq("수가코드 매핑 없음: 09"));
        verify(sendLogService, never()).recordPending(any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("무슨 일이 있어도 예외를 던지지 않는다 (확정은 성공해야 한다)")
    void neverThrows() {
        when(item.getLabItemCode()).thenReturn("01");
        when(receptionRepository.findByLabOrder_LabOrderIdAndReceptionStatusCodeOrderByCreatedAtDesc(anyString(), anyString()))
                .thenReturn(List.of());

        service.requestLabCharge(item); // 접수 없음 → 내부에서 잡는다
    }

    @Test
    @DisplayName("영상 청구: 원본=영상촬영항목ID, 접수=영상접수ID, 영상 수가 매핑을 쓴다 (Phase 7)")
    @SuppressWarnings("unchecked")
    void imageCharge() {
        BillingChargeService imageService = new BillingChargeService(
                new FeeCodeResolver(Map.of(), Map.of("CT01", "FEE-CT")), sendLogService, receptionRepository, "src-uuid", "06");

        imageService.requestImageCharge(imageItem("CT01"), "img-rec-1");

        ArgumentCaptor<Function<String, Object>> factory = ArgumentCaptor.forClass(Function.class);
        verify(sendLogService).recordPending(eq(SendEventType.BILLING), eq("img-item-1"), eq("06"), factory.capture());
        BillingChargeRequestData data = (BillingChargeRequestData) factory.getValue().apply("evt");
        assertThat(data.getReceptionId()).isEqualTo("img-rec-1");
        assertThat(data.getFeeCode()).isEqualTo("FEE-CT");
        assertThat(data.getSourceRecordId()).isEqualTo("img-item-1");
        assertThat(data.getPatientId()).isEqualTo("p-9");
    }

    @Test
    @DisplayName("영상 수가 매핑이 없으면 03 + '수가코드 매핑 없음' (임의 수가로 청구하지 않는다)")
    void imageChargeUnmapped() {
        service.requestImageCharge(imageItem("MR01"), "img-rec-1");

        verify(sendLogService).recordFailed(eq(SendEventType.BILLING), eq("img-item-1"), eq("06"), any(), eq("수가코드 매핑 없음: MR01"));
    }

    private static ImageOrderItemEntity imageItem(String code) {
        ImageOrderEntity order = mock(ImageOrderEntity.class);
        when(order.getPatientId()).thenReturn("p-9");
        ImageOrderItemEntity item = mock(ImageOrderItemEntity.class);
        when(item.getImageOrder()).thenReturn(order);
        when(item.getImageOrderItemId()).thenReturn("img-item-1");
        when(item.getImageItemCode()).thenReturn(code);
        return item;
    }

    @Test
    @DisplayName("기동 점검: 공통코드 중 매핑이 없는 코드를 찾는다")
    void mappingChecker() {
        CommonCodeCache cache = mock(CommonCodeCache.class);
        when(cache.getCodes("TEST_TYPE_CD")).thenReturn(Set.of("01", "02", "05"));

        Set<String> missing = new FeeCodeMappingChecker(cache, feeCodeResolver)
                .check("TEST_TYPE_CD", feeCodeResolver.labMappedCodes(), "app.billing.fee-code-mapping");

        assertThat(missing).containsExactly("02", "05");
    }
}
