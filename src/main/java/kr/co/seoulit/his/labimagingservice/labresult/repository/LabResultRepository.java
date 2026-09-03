package kr.co.seoulit.his.labimagingservice.labresult.repository;

import kr.co.seoulit.his.labimagingservice.labresult.entity.LabResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
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

    /**
     * 여러 검사항목의 결과를 한 번에 조회한다. (결과 등록 화면의 항목 목록 조립용)
     *
     * ⚠ 항목마다 결과를 조회하면 항목 수만큼 쿼리가 나간다(N+1).
     *   항목ID를 통째로 넘겨 IN 절 한 번으로 끝낸다.
     *   (SpecimenAcceptanceRepository.findBySpecimen_SpecimenIdIn 과 같은 용도)
     */
    List<LabResultEntity> findByLabOrderItem_LabOrderItemIdIn(Collection<String> labOrderItemIds);
}
