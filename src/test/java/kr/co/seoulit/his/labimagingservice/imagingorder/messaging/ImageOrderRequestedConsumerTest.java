package kr.co.seoulit.his.labimagingservice.imagingorder.messaging;

import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.cache.StaffDirectoryCache;
import kr.co.seoulit.his.labimagingservice.common.exception.LabImagingBusinessException;
import kr.co.seoulit.his.labimagingservice.imagingorder.dto.ImageOrderCreateRequestDto;
import kr.co.seoulit.his.labimagingservice.imagingorder.dto.ImageOrderSummaryDto;
import kr.co.seoulit.his.labimagingservice.imagingorder.messaging.dto.ImageOrderResultedData;
import kr.co.seoulit.his.labimagingservice.imagingorder.service.ImageOrderIntakeService;
import kr.co.seoulit.his.labimagingservice.imagingorder.service.ImageOrderService;
import kr.co.seoulit.his.labimagingservice.interfacelog.entity.InterfaceOrderType;
import kr.co.seoulit.his.labimagingservice.interfacelog.entity.InterfaceReceiveLogEntity;
import kr.co.seoulit.his.labimagingservice.interfacelog.service.InterfaceReceiveLogService;
import kr.co.seoulit.his.labimagingservice.laborder.messaging.dto.EventEnvelope;
import kr.co.seoulit.his.labimagingservice.laborder.messaging.dto.LabOrderResultStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
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
 * 영상오더 Kafka 수신 (5차 Phase 8) — 검사오더 Consumer 와 같은 멱등·거절 규칙.
 */
class ImageOrderRequestedConsumerTest {

    private final ImageOrderService imageOrderService = mock(ImageOrderService.class);
    private final ImageOrderResultedProducer producer = mock(ImageOrderResultedProducer.class);
    private final InterfaceReceiveLogService receiveLogService = mock(InterfaceReceiveLogService.class);
    private ImageOrderRequestedConsumer consumer;

    /** 실제 수신처럼 data 가 Map 으로 들어온다(봉투 제네릭 type erasure) */
    private final EventEnvelope<Object> envelope = new EventEnvelope<>("evt-1", "ImageOrderRequested", "1.0",
            OffsetDateTime.now(), "OPD", null,
            Map.of("prescriptionId", "RX-9", "patientId", "p-1", "doctorId", "d-1",
                    "orderItems", List.of(Map.of("itemCode", "CT01", "itemName", "Chest CT"))));

    @BeforeEach
    void setUp() {
        consumer = new ImageOrderRequestedConsumer(
                new ImageOrderIntakeService(imageOrderService, mock(StaffDirectoryCache.class)), producer,
                receiveLogService, JsonMapper.builder().build());
        when(receiveLogService.findByEventId("evt-1")).thenReturn(Optional.empty());
        when(receiveLogService.logReceived(any(), anyString(), anyString(), anyString(), anyString())).thenReturn("log-1");
        when(producer.publish(any(), anyString())).thenReturn(true);
    }

    @Test
    @DisplayName("정상 → IMG 로 수신로그(eventType 원문 포함), 코어 계약을 영상오더 생성 계약으로 바꿔 저장, ACCEPTED 발행")
    void accepted() {
        ImageOrderSummaryDto saved = mock(ImageOrderSummaryDto.class);
        when(saved.getImageOrderId()).thenReturn("io-1");
        when(imageOrderService.createOrder(any())).thenReturn(saved);

        consumer.onImageOrderRequested(envelope);

        verify(receiveLogService).logReceived(eq(InterfaceOrderType.IMG), eq("01"), anyString(), eq("evt-1"),
                eq("ImageOrderRequested"));
        ArgumentCaptor<ImageOrderCreateRequestDto> request = ArgumentCaptor.forClass(ImageOrderCreateRequestDto.class);
        verify(imageOrderService).createOrder(request.capture());
        assertThat(request.getValue().getImageOrderNo()).isEqualTo("RX-9");
        assertThat(request.getValue().getOrderItems()).singleElement()
                .extracting("imageItemCode").isEqualTo("CT01");
        assertThat(request.getValue().getReceivedById()).isEqualTo("SYSTEM");

        verify(receiveLogService).markResult("log-1", LabMessageCode.LAB001, null);
        ImageOrderResultedData result = captureResult();
        assertThat(result.getStatus()).isEqualTo(LabOrderResultStatus.ACCEPTED);
        assertThat(result.getImageOrderId()).isEqualTo("io-1");
    }

    @Test
    @DisplayName("업무 거절 → 결과코드 기록 + REJECTED 발행, 예외를 던지지 않는다(재시도 안 함)")
    void rejected() {
        when(imageOrderService.createOrder(any()))
                .thenThrow(new LabImagingBusinessException(LabMessageCode.LAB998, "유효하지 않은 환자ID입니다."));

        consumer.onImageOrderRequested(envelope);

        verify(receiveLogService).markResult("log-1", LabMessageCode.LAB998, "유효하지 않은 환자ID입니다.");
        ImageOrderResultedData result = captureResult();
        assertThat(result.getStatus()).isEqualTo(LabOrderResultStatus.REJECTED);
        assertThat(result.getReason()).isEqualTo("유효하지 않은 환자ID입니다.");
    }

    @Test
    @DisplayName("이미 끝난 이벤트가 다시 오면 저장하지 않고 저장된 결과로 재발행한다")
    void duplicateRepublishes() {
        InterfaceReceiveLogEntity done = mock(InterfaceReceiveLogEntity.class);
        when(done.getResultCode()).thenReturn(LabMessageCode.LAB001);
        when(receiveLogService.findByEventId("evt-1")).thenReturn(Optional.of(done));

        consumer.onImageOrderRequested(envelope);

        verify(imageOrderService, never()).createOrder(any());
        assertThat(captureResult().getStatus()).isEqualTo(LabOrderResultStatus.ACCEPTED);
    }

    @Test
    @DisplayName("처리 중(RECEIVED)으로 남은 이벤트는 같은 로그 행으로 다시 처리한다")
    void receivedIsRetried() {
        InterfaceReceiveLogEntity pending = mock(InterfaceReceiveLogEntity.class);
        when(pending.getResultCode()).thenReturn(InterfaceReceiveLogService.RESULT_RECEIVED);
        when(pending.getInterfaceReceiveLogId()).thenReturn("log-old");
        when(receiveLogService.findByEventId("evt-1")).thenReturn(Optional.of(pending));
        when(imageOrderService.createOrder(any())).thenReturn(mock(ImageOrderSummaryDto.class));

        consumer.onImageOrderRequested(envelope);

        verify(receiveLogService, never()).logReceived(any(), anyString(), anyString(), anyString(), anyString());
        verify(receiveLogService).markResult("log-old", LabMessageCode.LAB001, null);
    }

    @Test
    @DisplayName("그 밖의 예외는 다시 던진다(재시도·DLT) — 결과를 기록·발행하지 않는다")
    void unexpectedRethrown() {
        when(imageOrderService.createOrder(any())).thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> consumer.onImageOrderRequested(envelope)).isInstanceOf(IllegalStateException.class);
        verify(receiveLogService, never()).markResult(anyString(), anyString(), any());
        verify(producer, never()).publish(any(), anyString());
    }

    private ImageOrderResultedData captureResult() {
        ArgumentCaptor<ImageOrderResultedData> captor = ArgumentCaptor.forClass(ImageOrderResultedData.class);
        verify(producer).publish(captor.capture(), eq("evt-1"));
        return captor.getValue();
    }
}
