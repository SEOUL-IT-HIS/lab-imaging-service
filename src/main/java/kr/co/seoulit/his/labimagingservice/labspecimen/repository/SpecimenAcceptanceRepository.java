package kr.co.seoulit.his.labimagingservice.labspecimen.repository;

import kr.co.seoulit.his.labimagingservice.labspecimen.entity.SpecimenAcceptanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 검체 인수/적합성 판정 리포지토리.
 *
 * TODO: 조회 메서드 추가
 *   - 검체별 판정 이력 조회 (SPECIMEN 1건에 판정 여러 건 가능한지 먼저 확인할 것)
 */
public interface SpecimenAcceptanceRepository extends JpaRepository<SpecimenAcceptanceEntity, String> {
}
