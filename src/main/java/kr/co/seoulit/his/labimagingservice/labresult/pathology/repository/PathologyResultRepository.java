package kr.co.seoulit.his.labimagingservice.labresult.pathology.repository;

import kr.co.seoulit.his.labimagingservice.labresult.pathology.entity.PathologyResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PathologyResultRepository extends JpaRepository<PathologyResultEntity, String> {

    /** 검사항목당 1건 (UQ_PATH_LORI 에 걸리기 전에 LAB085 로 막는다) */
    boolean existsByLabOrderItem_LabOrderItemId(String labOrderItemId);

    /** 단건 + 검사항목 (응답의 labOrderItemId/labItemCode 용, 지연로딩 방지) */
    @Query("""
            select r from PathologyResultEntity r
            join fetch r.labOrderItem i
            where r.pathologyResultId = :id
            """)
    Optional<PathologyResultEntity> findDetailById(@Param("id") String id);

    /** 여러 항목의 결과를 IN 절 한 번으로 (접수 결과 목록·워크리스트 진행도, N+1 방지) */
    @Query("""
            select r from PathologyResultEntity r
            join fetch r.labOrderItem i
            where i.labOrderItemId in :itemIds
            """)
    List<PathologyResultEntity> findByItemIds(@Param("itemIds") List<String> itemIds);
}
