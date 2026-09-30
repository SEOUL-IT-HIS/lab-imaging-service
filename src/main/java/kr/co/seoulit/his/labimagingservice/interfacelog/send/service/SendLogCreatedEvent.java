package kr.co.seoulit.his.labimagingservice.interfacelog.send.service;

/**
 * 발신 이력(01)이 업무 트랜잭션 안에서 기록됐다는 스프링 내부 이벤트. (5차 Phase 5, D8)
 * InterfaceSendPublisher 가 AFTER_COMMIT 으로 받아 Kafka 로 발행한다. 커밋되지 않은(롤백된) 건은 발행되지 않는다.
 */
public record SendLogCreatedEvent(String interfaceSendLogId) {
}
