package kr.co.seoulit.his.labimagingservice.interfacelog.send.service;

import kr.co.seoulit.his.labimagingservice.interfacelog.send.repository.InterfaceSendLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 발신 이력 상태 갱신 전용 빈. (5차 Phase 5, D8)
 *
 * ⚠ 모든 메서드가 REQUIRES_NEW 다. 부르는 곳이 셋 다 "원래 트랜잭션이 없거나 이미 끝난" 자리이기 때문이다.
 *   1) AFTER_COMMIT 리스너 — 업무 트랜잭션은 이미 커밋됐다. 거기서 엔티티를 고쳐도 반영되지 않는다(D8 함정 1).
 *   2) Kafka send 콜백 — 프로듀서 스레드에서 돈다. 트랜잭션이 아예 없다(D8 함정 3).
 *   3) 재처리 스케줄러·수동 재전송 — 행마다 독립적으로 커밋돼야 한 건 실패가 다른 건을 막지 않는다.
 *
 * ⚠ 이 클래스를 InterfaceSendPublisher 안에 메서드로 두지 않은 이유 — 같은 클래스 안에서 @Transactional
 *   메서드를 부르면 프록시를 안 거쳐 트랜잭션이 걸리지 않는다(self-invocation, D8 함정 2).
 */
@Component
@RequiredArgsConstructor
public class InterfaceSendStatusUpdater {

    private final InterfaceSendLogRepository interfaceSendLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSent(String logId) {
        interfaceSendLogRepository.findById(logId).ifPresent(log -> log.markSent());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(String logId, String errorMessage) {
        interfaceSendLogRepository.findById(logId).ifPresent(log -> log.markFailed(errorMessage));
    }

    /** 재발행 직전 — 재시도 횟수 +1. (상태는 발행 결과 콜백이 02/03 으로 정한다) */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void beginRetry(String logId) {
        interfaceSendLogRepository.findById(logId).ifPresent(log -> log.beginRetry());
    }
}
