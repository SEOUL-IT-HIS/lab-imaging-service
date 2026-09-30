package kr.co.seoulit.his.labimagingservice.interfacelog.send.service;

import kr.co.seoulit.his.labimagingservice.interfacelog.send.entity.InterfaceSendLogEntity;
import kr.co.seoulit.his.labimagingservice.interfacelog.send.repository.InterfaceSendLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 발신 재처리 스케줄러. (5차 Phase 5, D8 흐름 4)
 *
 * 대상: 실패(03) 이거나, 대기(01)인데 stale-pending-minutes 보다 오래된 것 — 둘 다 재시도 횟수 < max-retry.
 * 처리: 행마다 retry_count +1 → 같은 event_id·같은 원문으로 다시 발행 → 콜백이 02/03 으로 갱신.
 *
 * ⚠ "오래된 01"을 다시 보내는 게 이 스케줄러의 두 번째 역할이다. 커밋 직후·발행 전에 서버가 죽으면
 *   AFTER_COMMIT 발행이 영영 안 일어난 01 행이 남는다(D8 함정 5). 그런 행을 여기서 메운다.
 * ⚠ 최대 횟수를 넘긴 건은 자동으로는 더 보내지 않는다. 사람이 조회 화면에서 수동 재전송한다(5-1).
 * ⚠ Kafka 가 꺼져 있으면 이 빈이 없다 — 행은 01 로 쌓이고, 켠 뒤 이 스케줄러가 보낸다(D8).
 *   켜고 끄는 스위치와 주기는 설정으로 뺐다(app.interface-send.retry.*).
 */
@Slf4j
@Component
@ConditionalOnExpression("${app.kafka.enabled:false} and ${app.interface-send.retry.enabled:true}")
public class InterfaceSendRetryScheduler {

    /** 한 번에 다시 보내는 최대 건수 — 장애 복구 직후 몰려 있어도 한 주기에 다 쏟지 않게 */
    private static final int BATCH_SIZE = 50;

    private final InterfaceSendLogRepository interfaceSendLogRepository;
    private final InterfaceSendStatusUpdater interfaceSendStatusUpdater;
    private final InterfaceSendPublisher interfaceSendPublisher;
    private final int maxRetry;
    private final long stalePendingMinutes;

    public InterfaceSendRetryScheduler(InterfaceSendLogRepository interfaceSendLogRepository,
                                       InterfaceSendStatusUpdater interfaceSendStatusUpdater,
                                       InterfaceSendPublisher interfaceSendPublisher,
                                       @Value("${app.interface-send.retry.max-retry:5}") int maxRetry,
                                       @Value("${app.interface-send.retry.stale-pending-minutes:5}") long stalePendingMinutes) {
        this.interfaceSendLogRepository = interfaceSendLogRepository;
        this.interfaceSendStatusUpdater = interfaceSendStatusUpdater;
        this.interfaceSendPublisher = interfaceSendPublisher;
        this.maxRetry = maxRetry;
        this.stalePendingMinutes = stalePendingMinutes;
    }

    @Scheduled(fixedDelayString = "${app.interface-send.retry.interval-ms:60000}",
            initialDelayString = "${app.interface-send.retry.interval-ms:60000}")
    public void retry() {
        List<InterfaceSendLogEntity> targets = interfaceSendLogRepository.findRetryTargets(
                LocalDateTime.now().minusMinutes(stalePendingMinutes), maxRetry, PageRequest.of(0, BATCH_SIZE));
        if (targets.isEmpty()) {
            return;
        }
        log.info("[SEND-RETRY] 재처리 대상 {}건", targets.size());
        for (InterfaceSendLogEntity target : targets) {
            try {
                interfaceSendStatusUpdater.beginRetry(target.getInterfaceSendLogId());
                interfaceSendPublisher.publish(target.getInterfaceSendLogId());
            } catch (RuntimeException e) {
                // 한 건의 실패가 나머지 건을 막지 않게 한다.
                log.error("[SEND-RETRY] 재처리 중 오류. logId={}", target.getInterfaceSendLogId(), e);
            }
        }
    }
}
