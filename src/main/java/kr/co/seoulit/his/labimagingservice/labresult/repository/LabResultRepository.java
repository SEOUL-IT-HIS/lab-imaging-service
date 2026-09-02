package kr.co.seoulit.his.labimagingservice.labresult.repository;

import kr.co.seoulit.his.labimagingservice.labresult.entity.LabResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 일반검사 결과 리포지토리.
 * (SpecimenAcceptanceRepository 와 같은 모양 — 1:1 상대 엔티티의 PK 로 찾고, 존재 여부로 중복을 막는다)
 */
public interface LabResultRepository extends JpaRepository<LabResultEntity, String> {

    /**
     * 검사항목ID로 결과를 찾는다.
     * lab_order_item_id 에 UNIQUE 가 걸려 있어 결과는 최대 1건이다.
     */
    Optional<LabResultEntity> findByLabOrderItem_LabOrderItemId(String labOrderItemId);

    /** 중복 등록 차단용. 검사항목 1건에 결과 1건이다. */
    boolean existsByLabOrderItem_LabOrderItemId(String labOrderItemId);
}
