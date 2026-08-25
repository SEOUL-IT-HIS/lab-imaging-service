package kr.co.seoulit.his.labimagingservice.interfacelog.repository;

import kr.co.seoulit.his.labimagingservice.interfacelog.entity.InterfaceReceiveLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 연계 수신 로그 리포지토리.
 *
 * TODO(후속): 수신 이력 조회 화면이 생기면 기간·출처·결과코드 검색 메서드를 추가한다.
 *   지금은 기록 전용이라 조회 메서드가 없다.
 */
public interface InterfaceReceiveLogRepository extends JpaRepository<InterfaceReceiveLogEntity, String> {
}
