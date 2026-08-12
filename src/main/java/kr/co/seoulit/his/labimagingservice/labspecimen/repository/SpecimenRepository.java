package kr.co.seoulit.his.labimagingservice.labspecimen.repository;

import kr.co.seoulit.his.labimagingservice.labspecimen.entity.SpecimenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * 검체 리포지토리.
 */
public interface SpecimenRepository extends JpaRepository<SpecimenEntity, String> {

    Optional<SpecimenEntity> findBySpecimenBarcode(String  specimenBarcode);

    boolean existsBySpecimenBarcode(String  specimenBarcode);

    /**
     * 적합성판정 대상(미판정) 검체 목록 조회. 최신 채취 건이 위로 오도록 created_at 내림차순.
     * (LabReceptionRepository.findUnscheduledWithLabOrder 와 같은 패턴 — 상세 설명은 그쪽 주석 참고)
     *
     * ── 미판정 필터: "not exists (해당 검체의 SPECIMEN_ACCEPTANCE)"
     *   - SPECIMEN : SPECIMEN_ACCEPTANCE 는 1:1 이라(specimen_id UNIQUE) 판정은 검체당 최대 1건이다.
     *     따라서 "판정 행이 없는 검체" = 아직 인수/적합성 판정을 안 한 검체다.
     *   - 이미 판정된 검체는 빠지므로, 화면에는 "판정 대상"만 남는다.
     *
     * ── N+1 방어: "join fetch s.labReception"
     *   - SpecimenEntity.labReception 은 @ManyToOne(FetchType.LAZY) 라, 매핑에서 건드리면
     *     행마다 SELECT 가 추가로 나갈 수 있다. join fetch 로 한 번에 가져온다.
     *   - @JoinColumn(nullable = false) 라 inner join fetch 로도 누락 행이 없다.
     */
    @Query("""
            select s from SpecimenEntity s
            join fetch s.labReception
            where not exists (
                select 1 from SpecimenAcceptanceEntity a
                 where a.specimen = s)
            order by s.createdAt desc
            """)
    List<SpecimenEntity> findUnjudgedWithLabReception();
}
