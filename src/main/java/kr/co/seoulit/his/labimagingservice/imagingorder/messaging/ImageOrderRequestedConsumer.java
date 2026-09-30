package kr.co.seoulit.his.labimagingservice.imagingorder.messaging;

import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.exception.DuplicateOrderException;
import kr.co.seoulit.his.labimagingservice.common.exception.LabImagingBusinessException;
import kr.co.seoulit.his.labimagingservice.imagingorder.dto.ImageOrderSummaryDto;
import kr.co.seoulit.his.labimagingservice.imagingorder.messaging.dto.ImageOrderRequestedData;
import kr.co.seoulit.his.labimagingservice.imagingorder.messaging.dto.ImageOrderResultedData;
import kr.co.seoulit.his.labimagingservice.imagingorder.service.ImageOrderIntakeService;
import kr.co.seoulit.his.labimagingservice.interfacelog.entity.InterfaceOrderType;
import kr.co.seoulit.his.labimagingservice.interfacelog.entity.InterfaceReceiveLogEntity;
import kr.co.seoulit.his.labimagingservice.interfacelog.service.InterfaceReceiveLogService;
import kr.co.seoulit.his.labimagingservice.laborder.messaging.dto.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

/**
 * 영상오더 요청 수신 (OPD 처방코어 → LAB). UC-IMG-01 (5차 Phase 8, 후속 #6, D12)
 * 토픽(안): opd.image-order.requested.v1 / 그룹: lab-service.image-order-requested-consumer — GR2 미합의, 설정값
 *
 * ⚠ LabOrderRequestedConsumer 를 고치지 않고 구조를 복제해 따로 만들었다(검사오더 경로 동작 불변 조건).
 *   규칙은 검사오더와 같다 — 자세한 이유는 LabOrderRequestedConsumer 주석 참고.
 *   ① event_id 멱등: 결과가 확정된 이벤트가 다시 오면 저장된 결과로 결과 이벤트를 재발행한다.
 *      RECEIVED(처리 중) 상태면 앞 시도가 안 끝난 것이라 다시 처리한다.
 *   ② 업무 거절(LabImagingBusinessException / DuplicateOrderException) → REJECTED 발행 후 정상 종료(재시도 안 함)
 *   ③ 그 밖의 예외 → 다시 던져 재시도·DLT (수신로그는 RECEIVED 로 남긴다)
 *
 * ⚠ 기본 꺼짐. app.kafka.enabled 와 app.kafka.image-order.enabled 가 모두 true 일 때만 빈이 생긴다.
 *   처방코어가 아직 이 토픽을 발행하지 않으므로, 켜기 전까지 기존 동작에 영향이 없다.
 */
@Slf4j
@Component
@ConditionalOnExpression("${app.kafka.enabled:false} and ${app.kafka.image-order.enabled:false}")
@RequiredArgsConstructor
public class ImageOrderRequestedConsumer {

    private final ImageOrderIntakeService imageOrderIntakeService;
    private final ImageOrderResultedProducer imageOrderResultedProducer;
    private final InterfaceReceiveLogService interfaceReceiveLogService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${app.kafka.topic.image-order-requested}",
            groupId = "${app.kafka.consumer-group.image-order-requested}")
    public void onImageOrderRequested(EventEnvelope<?> envelope) {

        ImageOrderRequestedData data = toRequestedData(envelope.getData());
        String eventId = envelope.getEventId();

        log.info("[KAFKA-IN ] image-order.requested | eventId={} | prescriptionId={} | items={}",
                shortId(eventId), data.getPrescriptionId(),
                data.getOrderItems() == null ? 0 : data.getOrderItems().size());

        // ① 멱등 확인
        Optional<InterfaceReceiveLogEntity> previous = interfaceReceiveLogService.findByEventId(eventId);
        if (previous.isPresent() && isFinished(previous.get())) {
            republishPreviousResult(previous.get(), data, eventId);
            return;
        }

        // ② 수신 기록 — 앞 시도의 행이 있으면 재사용(event_id UNIQUE). 봉투의 eventType 원문도 남긴다.
        String logId = previous
                .map(InterfaceReceiveLogEntity::getInterfaceReceiveLogId)
                .orElseGet(() -> interfaceReceiveLogService.logReceived(
                        InterfaceOrderType.IMG, ImageOrderIntakeService.SYSTEM_CODE_OUTPATIENT,
                        toRawMessage(data), eventId, envelope.getEventType()));

        try {
            ImageOrderSummaryDto saved = imageOrderIntakeService.intake(data);

            interfaceReceiveLogService.markResult(logId, LabMessageCode.LAB001, null);
            publishResult(ImageOrderResultedData.accepted(data.getPrescriptionId(), saved.getImageOrderId()), eventId);

        } catch (DuplicateOrderException e) {
            finishRejected(logId, eventId, data, e.getMessageCode(), e.getMessage());
        } catch (LabImagingBusinessException e) {
            finishRejected(logId, eventId, data, e.getMessageCode(), e.getMessage());
        }
        // ③ 그 밖의 예외는 잡지 않는다 → 에러 핸들러가 재시도·DLT
    }

    private boolean isFinished(InterfaceReceiveLogEntity log) {
        return !InterfaceReceiveLogService.RESULT_RECEIVED.equals(log.getResultCode());
    }

    private void finishRejected(String logId, String eventId, ImageOrderRequestedData data,
                                String resultCode, String reason) {
        log.info("[KAFKA-OUT] 영상오더 업무 거절 | eventId={} | reason={}", shortId(eventId), reason);
        interfaceReceiveLogService.markResult(logId, resultCode, reason);
        publishResult(ImageOrderResultedData.rejected(data.getPrescriptionId(), reason), eventId);
    }

    /**
     * 결과 발행. 실패는 Producer 가 ERROR 로그로 남긴다(검사오더와 같은 한계 — 코어가 같은 이벤트를 다시 보내면
     * ① 에서 저장된 결과로 재발행된다).
     * ⚠ 발행 실패를 수신로그 error_message 에 덮어쓰지 않는다. 그 칸은 거절 사유이고, 재발행 때 reason 으로 다시 나간다.
     */
    private void publishResult(ImageOrderResultedData result, String eventId) {
        imageOrderResultedProducer.publish(result, eventId);
    }

    /**
     * 저장된 결과로 다시 발행한다. LAB001 이면 접수, 그 외는 거절.
     * ⚠ 검사오더와 같은 한계 — 로그에 imageOrderId 가 없어 ACCEPTED 재발행에는 imageOrderId 가 비어 나간다.
     */
    private void republishPreviousResult(InterfaceReceiveLogEntity previous, ImageOrderRequestedData data, String eventId) {
        boolean accepted = LabMessageCode.LAB001.equals(previous.getResultCode());
        log.info("[KAFKA-DUP] eventId={} 이미 처리된 영상오더 이벤트 — 이전 결과 재발행 (resultCode={})",
                shortId(eventId), previous.getResultCode());

        ImageOrderResultedData result = accepted
                ? ImageOrderResultedData.accepted(data.getPrescriptionId(), null)
                : ImageOrderResultedData.rejected(data.getPrescriptionId(),
                        previous.getErrorMessage() == null ? "이미 처리된 요청입니다." : previous.getErrorMessage());
        publishResult(result, eventId);
    }

    /** 봉투가 제네릭이라 data 는 Map 으로 들어온다(type erasure). 여기서 한 번 변환한다. */
    ImageOrderRequestedData toRequestedData(Object rawData) {
        if (rawData instanceof ImageOrderRequestedData typed) {
            return typed;
        }
        return objectMapper.convertValue(rawData, ImageOrderRequestedData.class);
    }

    private String toRawMessage(ImageOrderRequestedData data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JacksonException e) {
            return "원문 직렬화 실패: " + e.getMessage();
        }
    }

    private String shortId(String id) {
        return (id == null || id.length() < 8) ? id : id.substring(0, 8);
    }
}
