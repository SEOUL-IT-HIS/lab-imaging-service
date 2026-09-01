package kr.co.seoulit.his.labimagingservice.labspecimen.repository;

import kr.co.seoulit.his.labimagingservice.labspecimen.entity.SpecimenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 검체 리포지토리.
 */
public interface SpecimenRepository extends JpaRepository<SpecimenEntity, String> {

    /**
     * 검체바코드로 단건 조회. (ZP2-75 바코드 검증 — SpecimenService.getSpecimenByBarcode)
     * specimen_barcode 에 UNIQUE 제약이 있어 결과는 최대 1건이다.
     */
    Optional<SpecimenEntity> findBySpecimenBarcode(String  specimenBarcode);

    /**
     * 채번한 바코드가 이미 쓰이고 있는지 확인한다.
     * (SpecimenService.generateSpecimenBarcode 의 재채번 루프에서 쓴다)
     */
    boolean existsBySpecimenBarcode(String  specimenBarcode);

    /**
     * TODO(ZP2-79 검체 이력 조회, 후반 작업): 아래 3개(미판정/판정완료/전체)는 화면에서 아직 쓰지 않는다.
     *   워크리스트 오른쪽 판정 패널은 접수별 목록(findByLabReception_ReceptionNoOrderByCreatedAtAsc)만 쓰고,
     *   판정 여부는 클라이언트에서 거른다. 판정 완료 건을 회색으로 남겨 "3건 중 2건 판정" 을 보여줘야 해서,
     *   서버에서 걸러 받으면 오히려 화면이 성립하지 않기 때문이다.
     *   접수를 넘나드는 검체 이력 조회 화면이 생기면 그때 이 3개가 쓰인다.
     */

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

    /**
     * 판정이 끝난 검체 목록. findUnjudgedWithLabReception 과 not exists / exists 만 다르다.
     * (LabReceptionRepository 의 미일정/일정등록 쌍과 같은 구조)
     */
    @Query("""
            select s from SpecimenEntity s
            join fetch s.labReception
            where exists (
                select 1 from SpecimenAcceptanceEntity a
                 where a.specimen = s)
            order by s.createdAt desc
            """)
    List<SpecimenEntity> findJudgedWithLabReception();

    /** 판정 여부와 무관한 전체 검체 목록. */
    @Query("""
            select s from SpecimenEntity s
            join fetch s.labReception
            order by s.createdAt desc
            """)
    List<SpecimenEntity> findAllWithLabReception();

    /**
     * 접수 1건의 검체 목록. (워크리스트 오른쪽 작업 폼에서 쓴다)
     * 채취한 순서대로 보는 게 자연스러워 asc 로 정렬한다.
     */
    List<SpecimenEntity> findByLabReception_ReceptionNoOrderByCreatedAtAsc(String receptionNo);

    /**
     * 여러 접수의 검체를 한 번에 조회한다. (워크리스트 진행상태 조립용)
     *
     * ⚠ 접수마다 검체를 조회하면 행 수만큼 쿼리가 나간다(N+1).
     *   접수ID 목록을 통째로 넘겨 IN 절 한 번으로 끝낸다.
     *   목록 쿼리에 left join 으로 붙이지 않는 이유는 LabReceptionRepository 주석 참고
     *   (SPECIMEN 은 접수와 1:N 이라 접수 행이 검체 수만큼 늘어난다).
     */
    List<SpecimenEntity> findByLabReception_LabReceptionIdIn(Collection<String> labReceptionIds);
}
