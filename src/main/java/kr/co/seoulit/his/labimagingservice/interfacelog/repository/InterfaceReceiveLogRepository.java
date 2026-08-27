package kr.co.seoulit.his.labimagingservice.interfacelog.repository;

import kr.co.seoulit.his.labimagingservice.interfacelog.entity.InterfaceReceiveLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 연계 수신 로그 리포지토리.
 *
 * TODO(후속): 수신 이력 조회 화면이 생기면 기간·출처·결과코드 검색 메서드를 추가한다.
 *   지금은 기록 전용이라 조회 메서드가 없다.
 */
public interface InterfaceReceiveLogRepository extends JpaRepository<InterfaceReceiveLogEntity, String> {

    /**
     * 이미 처리한 Kafka 이벤트인지 확인한다. (멱등 판정)
     *
     * ⚠ 조건부 UNIQUE 인덱스(UX_IRLG_EVENT)가 최종 방어선이지만, 그대로 두면 중복 수신 때
     *   DB 제약 위반이 그대로 올라온다. 여기서 먼저 확인해 "이미 처리함"으로 정상 처리한다.
     */
    Optional<InterfaceReceiveLogEntity> findByEventId(String eventId);

    /** 시연·디버깅용 최근 수신 이력. 화면이 없는 Kafka 연동을 눈으로 확인하는 유일한 창구다. */
    List<InterfaceReceiveLogEntity> findTop20ByOrderByReceivedAtDesc();
}
