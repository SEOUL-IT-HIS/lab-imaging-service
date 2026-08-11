package kr.co.seoulit.his.labimagingservice.labspecimen.repository;

import kr.co.seoulit.his.labimagingservice.labspecimen.entity.SpecimenEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 검체 리포지토리.
 *
 * TODO: 조회 메서드 추가 (LabOrderRepository 참고)
 *   - findBySpecimenBarcode(String)   바코드 검증/인수 처리용 (ZP2-75)
 *   - existsBySpecimenBarcode(String) 바코드 중복 발행 방지 (ZP2-65)
 *   - 접수건별 검체 목록 조회 (ZP2-79 검체 이력)
 */
public interface SpecimenRepository extends JpaRepository<SpecimenEntity, String> {
}
