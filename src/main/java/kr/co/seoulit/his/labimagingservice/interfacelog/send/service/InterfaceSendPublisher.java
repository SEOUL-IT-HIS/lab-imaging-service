package kr.co.seoulit.his.labimagingservice.interfacelog.send.service;

import kr.co.seoulit.his.labimagingservice.billing.messaging.dto.BillingChargeRequestData;
import kr.co.seoulit.his.labimagingservice.interfacelog.send.SendEventType;
import kr.co.seoulit.his.labimagingservice.interfacelog.send.entity.InterfaceSendLogEntity;
import kr.co.seoulit.his.labimagingservice.interfacelog.send.repository.InterfaceSendLogRepository;
import kr.co.seoulit.his.labimagingservice.laborder.messaging.dto.EventEnvelope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;

/**
 * 발신 이력 → Kafka 발행기. (5차 Phase 5, D8)
 *
 * ══ 왜 @TransactionalEventListener(AFTER_COMMIT) 인가 ══
 *   트랜잭션 안에서 바로 발행하면, 그 뒤에 트랜잭션이 롤백돼도 메시지는 이미 나간다 — "확정되지 않은 결과"가
 *   수납·처방코어에 전달된다. 서비스마다 커밋 후에 수동으로 부르게 하면 호출하는 곳마다 같은 처리를 반복한다.
 *   AFTER_COMMIT 은 "커밋이 확정된 것만 발행"을 스프링 표준 방식으로 보장한다.
 *
 * ══ 반드시 지킬 것 (D8 함정 1~5) ══
 *   ⚠ 1) 이 리스너 안에서 엔티티를 고쳐도 원래 트랜잭션은 이미 끝나 반영되지 않는다.
 *        상태 갱신은 InterfaceSendStatusUpdater(REQUIRES_NEW)로 한다.
 *   ⚠ 2) 그 갱신 메서드를 이 클래스에 두지 않는다 — 같은 클래스 호출은 프록시를 안 거쳐 트랜잭션이 안 걸린다.
 *   ⚠ 3) Kafka send 콜백은 프로듀서 스레드에서 돈다. 콜백의 갱신도 1)의 별도 트랜잭션 메서드로 한다.
 *   ⚠ 4) 이벤트는 @Transactional 서비스 안에서 발행돼야 이 리스너가 돈다(InterfaceSendLogService 가 확인).
 *        fallbackExecution 은 쓰지 않는다 — 트랜잭션 없이 발행된 건 버그로 드러나야 한다.
 *   ⚠ 5) 커밋 직후·발행 전에 서버가 죽으면 행은 01 로 남는다. 그 틈은 InterfaceSendRetryScheduler 가
 *        "오래된 01"을 다시 보내 메운다.
 *
 * ⚠ 발행하는 메시지는 이력에 저장한 원문 그대로다. 재발행해도 event_id 와 내용이 바뀌지 않는다.
 *   원문을 원래 클래스(청구=BillingChargeRequestData, 결과=EventEnvelope)로 되살려 보낸다 — JsonNode 로 보내면
 *   본문은 같아도 Jackson 타입 헤더(__TypeId__)가 바뀌어, 기존 청구 발행과 헤더까지 같게 유지할 수 없다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class InterfaceSendPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final InterfaceSendLogRepository interfaceSendLogRepository;
    private final InterfaceSendStatusUpdater interfaceSendStatusUpdater;
    private final ObjectMapper objectMapper;
    private final String billingChargeTopic;
    private final String labResultReportedTopic;

    public InterfaceSendPublisher(KafkaTemplate<String, Object> kafkaTemplate,
                                  InterfaceSendLogRepository interfaceSendLogRepository,
                                  InterfaceSendStatusUpdater interfaceSendStatusUpdater,
                                  ObjectMapper objectMapper,
                                  @Value("${app.kafka.topic.billing-charge}") String billingChargeTopic,
                                  @Value("${app.kafka.topic.lab-result-reported}") String labResultReportedTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.interfaceSendLogRepository = interfaceSendLogRepository;
        this.interfaceSendStatusUpdater = interfaceSendStatusUpdater;
        this.objectMapper = objectMapper;
        this.billingChargeTopic = billingChargeTopic;
        this.labResultReportedTopic = labResultReportedTopic;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCreated(SendLogCreatedEvent event) {
        publish(event.interfaceSendLogId());
    }

    /**
     * 이력 1건을 발행한다. 결과는 비동기 콜백이 02/03 으로 기록한다. 예외를 밖으로 던지지 않는다.
     * (AFTER_COMMIT 리스너·스케줄러·수동 재전송이 같이 쓴다)
     */
    public void publish(String logId) {
        InterfaceSendLogEntity sendLog = interfaceSendLogRepository.findById(logId).orElse(null);
        if (sendLog == null) {
            return;
        }

        Target target;
        try {
            target = resolveTarget(sendLog);
        } catch (RuntimeException e) {
            // 원문을 되살리지 못하거나(없음·손상) 보낼 수 없는 원문(수가코드 없음 등)이다. 재시도해도 같다.
            interfaceSendStatusUpdater.markFailed(logId, "발행 준비 실패: " + e.getMessage());
            log.error("[KAFKA-OUT] 발행 준비 실패. logId={} eventId={}", logId, sendLog.getEventId(), e);
            return;
        }

        try {
            kafkaTemplate.send(target.topic(), target.key(), target.payload())
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            interfaceSendStatusUpdater.markSent(logId);
                            log.info("[KAFKA-OUT] {} | eventId={} | referenceId={} | key={}",
                                    target.topic(), sendLog.getEventId(), sendLog.getReferenceId(), target.key());
                        } else {
                            interfaceSendStatusUpdater.markFailed(logId, ex.getMessage());
                            log.error("[KAFKA-OUT] 발행 실패. topic={} eventId={} referenceId={}",
                                    target.topic(), sendLog.getEventId(), sendLog.getReferenceId(), ex);
                        }
                    });
        } catch (RuntimeException e) {
            // 브로커 메타데이터 대기 초과(max.block.ms) 등 send() 자체가 동기로 실패한 경우
            interfaceSendStatusUpdater.markFailed(logId, e.getMessage());
            log.error("[KAFKA-OUT] 발행 실패(동기). topic={} eventId={}", target.topic(), sendLog.getEventId(), e);
        }
    }

    record Target(String topic, String key, Object payload) {
    }

    /** 이벤트 유형별 토픽·키·메시지. 키는 원문에서 꺼낸다(이력 테이블에 키 컬럼이 없다). */
    Target resolveTarget(InterfaceSendLogEntity sendLog) {
        if (sendLog.getPayload() == null) {
            throw new IllegalStateException("발행할 원문이 없습니다. (" + sendLog.getErrorMessage() + ")");
        }
        SendEventType type = SendEventType.fromCode(sendLog.getEventTypeCode());
        return switch (type) {
            case BILLING -> {
                BillingChargeRequestData data = objectMapper.readValue(sendLog.getPayload(), BillingChargeRequestData.class);
                if (data.getFeeCode() == null) {
                    throw new IllegalStateException("수가코드가 없는 청구 원문입니다.");
                }
                // 파티션 키 = receptionId (기존 청구 발행과 같다 — 같은 접수의 청구가 같은 파티션에 순서대로)
                yield new Target(billingChargeTopic, data.getReceptionId(), data);
            }
            case RESULT -> {
                // ⚠ Jackson 3 는 기본으로 OffsetDateTime 을 UTC 로 바꿔 읽는다(+09:00 → Z). 그대로 두면 재발행 원문의
                //   occurredAt 형식이 최초 발행과 달라진다(같은 시각이지만 계약 형식 +09:00 위반). 오프셋을 유지해 읽는다.
                EventEnvelope<?> envelope = objectMapper.readerFor(EventEnvelope.class)
                        .without(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                        .readValue(sendLog.getPayload());
                // 메시지 키 = prescriptionId (검사오더 연계와 같은 키 — 한 처방의 이벤트 순서 보장)
                JsonNode data = objectMapper.readTree(sendLog.getPayload()).path("data");
                yield new Target(labResultReportedTopic, data.path("prescriptionId").asString(null), envelope);
            }
        };
    }
}
