package kr.co.seoulit.his.labimagingservice.laborder.messaging;

import kr.co.seoulit.his.labimagingservice.laborder.messaging.dto.EventEnvelope;
import kr.co.seoulit.his.labimagingservice.laborder.messaging.dto.LabOrderResultedData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 검사오더 접수 결과 발행 (LAB → OPD)
 * 토픽: lab.lab-order.resulted.v1
 *
 * ⚠ 메시지 키는 prescriptionId 다. (OPD 문서 03장)
 *   같은 처방의 이벤트가 같은 파티션에 쌓여 순서가 보장된다.
 *   키를 안 주면 라운드로빈으로 흩어져, 재발행분이 원본보다 먼저 도착할 수 있다.
 *
 * ⚠ 발행이 실패하면 오더는 저장됐는데 코어는 영영 모른다.
 *   정석은 Outbox 패턴(같은 트랜잭션에 이벤트를 저장하고 별도 발행기가 읽어 보내는 방식)이지만
 *   지금 범위에는 과하다. 대신 발행 실패를 INTERFACE_RECEIVE_LOG.error_message 에 남기고 넘어간다.
 *   OPD 도 5분 타임아웃 후 PENDING 처리 정책이 있어 완전히 유실되지는 않는다.
 *   이 한계를 알고 쓰는 것과 모르고 쓰는 것은 다르므로 명시해 둔다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
@RequiredArgsConstructor
public class LabOrderResultedProducer {

    private static final String EVENT_TYPE = "LabOrderResulted";
    private static final String VERSION = "1.0";
    private static final String SOURCE = "LAB";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topic.lab-order-resulted}")
    private String resultedTopic;

    /**
     * 접수 결과를 발행한다.
     *
     * @param correlationId 수신한 requested 이벤트의 eventId.
     *                      ⚠ 새 UUID 를 넣으면 안 된다. 코어가 "어느 요청에 대한 응답인지"를
     *                        이 값으로 잇는다. (문서 04장 규정)
     * @return 발행 성공 여부. 실패해도 예외를 던지지 않는다 — 호출한 쪽이 로그에 남기고 진행한다.
     */
    public boolean publish(LabOrderResultedData data, String correlationId) {
        EventEnvelope<LabOrderResultedData> envelope = new EventEnvelope<>(
                UUID.randomUUID().toString(),
                EVENT_TYPE,
                VERSION,
                OffsetDateTime.now(),
                SOURCE,
                correlationId,
                data);

        try {
            // 키 = prescriptionId. 같은 처방의 순서 보장.
            kafkaTemplate.send(resultedTopic, data.getPrescriptionId(), envelope).get();

            log.info("[KAFKA-OUT] {} | eventId={} | status={} | labOrderId={} | reason={}",
                    resultedTopic, shortId(envelope.getEventId()),
                    data.getStatus(), data.getLabOrderId(), data.getReason());
            return true;

        } catch (InterruptedException e) {
            // ⚠ 인터럽트는 삼키면 안 된다. 스레드 상태를 되돌려 놓고 실패로 처리한다.
            Thread.currentThread().interrupt();
            log.error("[KAFKA-OUT] 발행 중 인터럽트. prescriptionId={}", data.getPrescriptionId(), e);
            return false;

        } catch (Exception e) {
            log.error("[KAFKA-OUT] 발행 실패. prescriptionId={} status={}",
                    data.getPrescriptionId(), data.getStatus(), e);
            return false;
        }
    }

    /** 로그를 한눈에 보려고 UUID 앞 8자리만 찍는다. 추적에는 이 정도면 충분하다. */
    private String shortId(String id) {
        return (id == null || id.length() < 8) ? id : id.substring(0, 8);
    }
}
