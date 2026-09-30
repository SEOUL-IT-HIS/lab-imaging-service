package kr.co.seoulit.his.labimagingservice.imagingorder.messaging;

import kr.co.seoulit.his.labimagingservice.imagingorder.messaging.dto.ImageOrderResultedData;
import kr.co.seoulit.his.labimagingservice.laborder.messaging.dto.EventEnvelope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * 영상오더 접수 결과 발행 (LAB → OPD). UC-IMG-01 (5차 Phase 8, D12)
 * 토픽(안): lab.image-order.resulted.v1 — GR2 미합의, 설정값
 *
 * ⚠ LabOrderResultedProducer 를 고치지 않고 따로 만들었다(검사오더 경로 동작 불변 조건). 규칙은 같다:
 *   키 = prescriptionId(같은 처방 순서 보장), correlationId = 수신한 requested 이벤트의 eventId,
 *   발행 실패는 예외 대신 false — 호출측이 수신로그에 남긴다.
 * ⚠ app.kafka.enabled 와 app.kafka.image-order.enabled 가 모두 true 일 때만 빈이 생긴다.
 */
@Slf4j
@Component
@ConditionalOnExpression("${app.kafka.enabled:false} and ${app.kafka.image-order.enabled:false}")
public class ImageOrderResultedProducer {

    static final String EVENT_TYPE = "ImageOrderResulted";
    private static final String VERSION = "1.0";
    private static final String SOURCE = "LAB";

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String resultedTopic;

    public ImageOrderResultedProducer(KafkaTemplate<String, Object> kafkaTemplate,
                                      @Value("${app.kafka.topic.image-order-resulted}") String resultedTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.resultedTopic = resultedTopic;
    }

    public boolean publish(ImageOrderResultedData data, String correlationId) {
        EventEnvelope<ImageOrderResultedData> envelope = new EventEnvelope<>(
                UUID.randomUUID().toString(), EVENT_TYPE, VERSION,
                OffsetDateTime.now(ZoneOffset.ofHours(9)), SOURCE, correlationId, data);
        try {
            kafkaTemplate.send(resultedTopic, data.getPrescriptionId(), envelope).get();
            log.info("[KAFKA-OUT] {} | status={} | imageOrderId={} | reason={}",
                    resultedTopic, data.getStatus(), data.getImageOrderId(), data.getReason());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[KAFKA-OUT] 영상오더 결과 발행 중 인터럽트. prescriptionId={}", data.getPrescriptionId(), e);
            return false;
        } catch (Exception e) {
            log.error("[KAFKA-OUT] 영상오더 결과 발행 실패. prescriptionId={} status={}",
                    data.getPrescriptionId(), data.getStatus(), e);
            return false;
        }
    }
}
