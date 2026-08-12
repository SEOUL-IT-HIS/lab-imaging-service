package kr.co.seoulit.his.labimagingservice.labspecimen.repository;

import kr.co.seoulit.his.labimagingservice.labspecimen.entity.SpecimenEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 검체 리포지토리.
 *
 * TODO: 조회 메서드 추가 (LabOrderRepository 참고)
 *   - 접수건별 검체 목록 조회 (ZP2-79 검체 이력)
 */
public interface SpecimenRepository extends JpaRepository<SpecimenEntity, String> {

    Optional<SpecimenEntity> findBySpecimenBarcode(String  specimenBarcode);

    boolean existsBySpecimenBarcode(String  specimenBarcode);
}
