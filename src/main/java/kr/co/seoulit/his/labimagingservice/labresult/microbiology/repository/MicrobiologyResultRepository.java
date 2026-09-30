package kr.co.seoulit.his.labimagingservice.labresult.microbiology.repository;

import kr.co.seoulit.his.labimagingservice.labresult.microbiology.entity.MicrobiologyResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MicrobiologyResultRepository extends JpaRepository<MicrobiologyResultEntity, String> {

    /** 이 검체에 이미 결과가 있는가 (UQ_MBRS_SPEC 에 걸리기 전에 메시지로 막는다) */
    boolean existsBySpecimen_SpecimenId(String specimenId);

    /** 이 접수의 검체 중 하나라도 미생물 결과가 있는가 (접수당 1건 제약, 5차 결정) */
    boolean existsBySpecimen_LabReception_LabReceptionId(String labReceptionId);

    /**
     * 단건 + 감수성 목록 + 검체·접수까지 한 번에. (상세 응답용)
     * ⚠ susceptibilities 를 fetch 하지 않으면 매핑 중 지연로딩 쿼리가 따로 나간다.
     */
    @Query("""
            select distinct r from MicrobiologyResultEntity r
            join fetch r.specimen s
            join fetch s.labReception rec
            left join fetch r.susceptibilities
            where r.microbiologyResultId = :id
            """)
    Optional<MicrobiologyResultEntity> findDetailById(@Param("id") String id);

    /** 접수번호로 조회 (결과 탭). 접수당 1건 제약이라 보통 0~1건이다. */
    @Query("""
            select distinct r from MicrobiologyResultEntity r
            join fetch r.specimen s
            join fetch s.labReception rec
            left join fetch r.susceptibilities
            where rec.receptionNo = :receptionNo
            """)
    List<MicrobiologyResultEntity> findDetailByReceptionNo(@Param("receptionNo") String receptionNo);

    /** 워크리스트 진행도 — 여러 접수를 IN 절 한 번으로. (LabWorklistService, N+1 방지) */
    @Query("""
            select r from MicrobiologyResultEntity r
            join fetch r.specimen s
            join fetch s.labReception rec
            where rec.labReceptionId in :receptionIds
            """)
    List<MicrobiologyResultEntity> findByReceptionIds(@Param("receptionIds") List<String> receptionIds);
}
