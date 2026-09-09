package kr.co.seoulit.his.labimagingservice.billing.messaging;

import kr.co.seoulit.his.labimagingservice.billing.messaging.dto.BillingChargeRequestData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 검사결과 확정 → 수납 청구 이벤트 발행 (LAB → 수납)
 * 토픽: exam-billing-charge
 *
 * ⚠ LabOrderResultedProducer 와 구조를 맞췄다 — 실패해도 예외를 던지지 않고 boolean 만 돌려준다.
 *   확정(LabResultService.confirmLabResult)은 이미 커밋된 트랜잭션이라, 발행 실패로
 *   그 확정 자체를 롤백하면 안 된다. Outbox 패턴(같은 트랜잭션에 이벤트를 저장하고 별도
 *   발행기가 읽어 보내는 방식)이 정석이지만, 수납팀과 이번 범위에서는 적용하지 않기로
 *   확정했다 — 실패는 로그로만 남긴다. (LabOrderResultedProducer 주석과 같은 한계)
 *
 * ⚠ EventEnvelope 로 감싸지 않는다. BillingChargeRequestData 를 그대로 발행한다.
 *   OPD 연동과 계약 형태 자체가 다르다 — 수납 쪽은 봉투가 아니라 평문 객체 하나다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
@RequiredArgsConstructor
public class BillingChargeProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topic.billing-charge}")
    private String billingChargeTopic;

    /**
     * 청구 이벤트를 발행한다.
     *
     * ⚠ 파티션 키는 receptionId 다. 같은 접수의 청구 이벤트가 같은 파티션에 쌓여 순서가 보장된다.
     *
     * @return 발행 성공 여부. 실패해도 예외를 던지지 않는다 — 호출한 쪽이 로그에 남기고 진행한다.
     */
    public boolean publish(BillingChargeRequestData data) {
        try {
            kafkaTemplate.send(billingChargeTopic, data.getReceptionId(), data).get();

            log.info("[KAFKA-OUT] {} | receptionId={} | sourceRecordId={} | feeCode={}",
                    billingChargeTopic, data.getReceptionId(), data.getSourceRecordId(), data.getFeeCode());
            return true;

        } catch (InterruptedException e) {
            // ⚠ 인터럽트는 삼키면 안 된다. 스레드 상태를 되돌려 놓고 실패로 처리한다.
            Thread.currentThread().interrupt();
            log.error("[KAFKA-OUT] 청구 이벤트 발행 중 인터럽트. receptionId={}", data.getReceptionId(), e);
            return false;

        } catch (Exception e) {
            log.error("[KAFKA-OUT] 청구 이벤트 발행 실패. receptionId={} sourceRecordId={}",
                    data.getReceptionId(), data.getSourceRecordId(), e);
            return false;
        }
    }
}
