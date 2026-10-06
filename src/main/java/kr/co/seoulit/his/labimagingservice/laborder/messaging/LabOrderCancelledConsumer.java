package kr.co.seoulit.his.labimagingservice.laborder.messaging;

import kr.co.seoulit.his.labimagingservice.common.exception.OrderNotYetReceivedException;
import kr.co.seoulit.his.labimagingservice.interfacelog.entity.InterfaceOrderType;
import kr.co.seoulit.his.labimagingservice.interfacelog.entity.InterfaceReceiveLogEntity;
import kr.co.seoulit.his.labimagingservice.interfacelog.service.InterfaceReceiveLogService;
import kr.co.seoulit.his.labimagingservice.laborder.dto.LabOrderCancelResultDto;
import kr.co.seoulit.his.labimagingservice.laborder.messaging.dto.EventEnvelope;
import kr.co.seoulit.his.labimagingservice.laborder.messaging.dto.LabOrderCancelledData;
import kr.co.seoulit.his.labimagingservice.laborder.repository.LabOrderRepository;
import kr.co.seoulit.his.labimagingservice.laborder.service.LabOrderCancelCommand;
import kr.co.seoulit.his.labimagingservice.laborder.service.LabOrderCancelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 처방 비활성화(검사오더 취소) 수신 (OPD → LAB)
 * 토픽: opd.lab-order.cancelled.v1 (05번 지시서)
 *
 * ⚠ LabOrderRequestedConsumer 와 같은 구조(①멱등 확인 ②수신 기록 ③처리 ④그 밖의 예외는 다시 던짐)를
 *   그대로 따른다. 다른 점은 딱 하나 — 취소 이벤트에는 "결과 회신" 개념이 없다(코어가 회신을
 *   기다리지 않는다). 그래서 이미 처리된 이벤트를 다시 받아도 재발행할 것이 없어 조용히 종료한다.
 *
 * ⚠ 업무 거절(전부 REFUSED 포함)은 재시도하지 않는다. 이미 끝난 일(결과 등록됨 등)은
 *   100번 다시 봐도 같은 결론이다. 재시도 대상은 오직 "오더를 아직 못 찾음"
 *   (OrderNotYetReceivedException, 순서 역전) 뿐이다 — 아래 ④ 참고.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
@RequiredArgsConstructor
public class LabOrderCancelledConsumer {

    private static final int ERROR_MESSAGE_MAX_LENGTH = 500;

    private final LabOrderCancelService labOrderCancelService;
    private final LabOrderRepository labOrderRepository;
    private final InterfaceReceiveLogService interfaceReceiveLogService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${app.kafka.topic.lab-order-cancelled:opd.lab-order.cancelled.v1}",
            groupId = "${app.kafka.consumer-group.lab-order-cancelled:lab-service.lab-order-cancelled-consumer}")
    public void onLabOrderCancelled(EventEnvelope<LabOrderCancelledData> envelope) {

        LabOrderCancelledData data = toCancelledData(envelope.getData());
        String eventId = envelope.getEventId();

        log.info("[KAFKA-IN ] opd.lab-order.cancelled.v1 | eventId={} | prescriptionId={} | items={}",
                shortId(eventId), data.getPrescriptionId(),
                data.getCancelledItems() == null ? 0 : data.getCancelledItems().size());

        /*
         * ① 멱등 확인.
         *
         * ⚠ 취소 이벤트는 처리 결과를 코어에 되돌려줄 필요가 없다(요청 이벤트와 다름 — 코어는
         *   취소를 "요청만" 하고 결과를 기다리지 않는다, 05번 지시서 §0). 그래서 이미 끝난
         *   이벤트를 다시 받으면 재발행할 것도 없이 그냥 종료한다.
         */
        Optional<InterfaceReceiveLogEntity> previous = interfaceReceiveLogService.findByEventId(eventId);
        if (previous.isPresent() && isFinished(previous.get())) {
            log.info("[KAFKA-DUP] eventId={} 이미 처리된 이벤트입니다. 무시합니다. (resultCode={})",
                    shortId(eventId), previous.get().getResultCode());
            return;
        }

        /*
         * ② 수신 기록.
         *
         * ⚠ 앞 시도가 남긴 행이 있으면 재사용한다(UX_IRLG_EVENT 조건부 UNIQUE 회피).
         *   systemCode 는 기존 오더를 찾을 수 있으면 그 오더의 값을, 못 찾으면(순서 역전 등)
         *   봉투의 source 를 쓴다(10자 초과 시 잘라서 — system_code 컬럼이 VARCHAR2(10)).
         */
        String systemCode = labOrderRepository.findByLabOrderNo(data.getPrescriptionId())
                .map(order -> order.getSystemCode())
                .orElseGet(() -> truncate(envelope.getSource(), 10));

        String logId = previous
                .map(InterfaceReceiveLogEntity::getInterfaceReceiveLogId)
                .orElseGet(() -> interfaceReceiveLogService.logReceived(
                        InterfaceOrderType.LAB, systemCode, toRawMessage(data), eventId, "LabOrderCancelled"));

        try {
            // ③ 취소 처리.
            LabOrderCancelResultDto result = labOrderCancelService.cancel(toCommand(data));

            interfaceReceiveLogService.markResult(logId, result.getCode(), itemSummary(result));

            log.info("[KAFKA-OUT] 취소 처리 완료 | eventId={} | prescriptionId={} | outcome={}",
                    shortId(eventId), data.getPrescriptionId(), result.getOutcome());

        } catch (OrderNotYetReceivedException e) {
            /*
             * ④ 오더를 아직 못 찾음 — 다시 던진다. 순서 역전(취소 이벤트가 접수 처리보다 먼저
             *   도착)은 재시도하면 풀릴 수 있는 상황이다(05번 지시서 2-D). KafkaConfig 의
             *   재시도 제외 목록에 이 예외를 올리지 않았으므로 기본 정책(1s·2s·4s 후 DLT)을 탄다.
             *
             * ⚠ 수신 기록을 일부러 RECEIVED 로 남겨 둔다 — "아직 끝내 처리되지 못했다"는 사실을
             *   정확히 나타낸다. 재시도를 모두 소진해 DLT 로 가면 이 행은 RECEIVED 로 영구히
             *   남는데, 이 경우는 드물고 수동 확인이 필요하다는 것을 운영 보고서에 남긴다.
             */
            log.warn("[KAFKA-IN ] eventId={} prescriptionId={} 의 오더를 아직 찾을 수 없습니다. 재시도합니다.",
                    shortId(eventId), data.getPrescriptionId());
            throw e;
        }
        /*
         * ⚠ 그 밖의 예외(DB 커넥션 끊김 등)도 잡지 않는다 — LabOrderRequestedConsumer 와 같은 이유.
         *   재시도하면 성공할 수 있는 실패는 에러 핸들러가 재시도·DLT 로 처리해야 한다.
         */
    }

    /** 결과코드가 채워졌으면 처리가 끝난 이벤트다. RECEIVED 는 아직 처리 중이라는 뜻이다. */
    private boolean isFinished(InterfaceReceiveLogEntity log) {
        return !InterfaceReceiveLogService.RESULT_RECEIVED.equals(log.getResultCode());
    }

    /** 항목별 판정 요약. 수신로그 error_message 컬럼 길이(500자)에 맞춰 자른다. */
    private String itemSummary(LabOrderCancelResultDto result) {
        String summary = result.getItems().stream()
                .map(item -> item.getItemCode() + ":" + item.getResult())
                .collect(Collectors.joining(", "));
        return truncate(summary, ERROR_MESSAGE_MAX_LENGTH);
    }

    /**
     * 봉투의 data 를 목표 타입으로 맞춘다. (EventEnvelope 제네릭 타입 소실 — type erasure)
     * LabOrderRequestedConsumer.toRequestedData 와 같은 이유·같은 방식이다.
     */
    private LabOrderCancelledData toCancelledData(Object rawData) {
        if (rawData instanceof LabOrderCancelledData typed) {
            return typed;
        }
        return objectMapper.convertValue(rawData, LabOrderCancelledData.class);
    }

    /** Kafka 계약 → 서비스 내부 계약 변환. */
    private LabOrderCancelCommand toCommand(LabOrderCancelledData data) {
        List<LabOrderCancelCommand.Item> items = data.getCancelledItems() == null
                ? List.of()
                : data.getCancelledItems().stream()
                        .map(item -> new LabOrderCancelCommand.Item(item.getItemCode(), item.getLabOrderId()))
                        .toList();

        return new LabOrderCancelCommand(data.getPrescriptionId(), data.getCancelReason(), data.getCancelledBy(), items);
    }

    /** 수신 원문. 직렬화가 실패해도 처리를 막지 않는다. */
    private String toRawMessage(LabOrderCancelledData data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JacksonException e) {
            return "원문 직렬화 실패: " + e.getMessage();
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    /** 로그를 한눈에 보려고 UUID 앞 8자리만 찍는다. */
    private String shortId(String id) {
        return (id == null || id.length() < 8) ? id : id.substring(0, 8);
    }
}
