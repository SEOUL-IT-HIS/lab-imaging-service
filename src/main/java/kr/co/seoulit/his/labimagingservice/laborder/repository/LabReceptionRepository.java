package kr.co.seoulit.his.labimagingservice.laborder.repository;

import kr.co.seoulit.his.labimagingservice.laborder.entity.LabReceptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface LabReceptionRepository extends JpaRepository<LabReceptionEntity, String> {

    Optional<LabReceptionEntity> findByReceptionNo(String receptionNo);

    /*
     * 일정 등록 여부로 거르던 목록 3종(findUnscheduled/findScheduled/findAllWithLabOrder)은
     * 삭제했다. (2026-08-14) 아래 워크리스트 조회가 대체한다.
     */

    /**
     * 워크리스트용 접수 목록. 접수상태(ACCEPTED=처리 대상 / EXCLUDED=제외됨)로 거른다.
     * 오래 대기한 건이 위로 오도록 접수일시 오름차순이다.
     * (새 오더가 들어오면 목록 아래에 붙는 게 담당자 입장에서 자연스럽다)
     *
     * ── N+1 방어의 핵심: "join fetch r.labOrder"
     *   - LabReceptionEntity.labOrder 는 @ManyToOne(FetchType.LAZY) 다. 그냥 목록만 가져온 뒤
     *     매핑에서 각 건의 getLabOrder() 를 건드리면 그 순간마다 SELECT 가 1번씩 추가로 나간다.
     *     → 목록 1번 + 행마다 N번 = 총 N+1 쿼리 (접수 20건이면 21쿼리). 행이 늘수록 급격히 느려진다.
     *   - "join fetch" 는 labOrder 를 목록 쿼리와 "한 방"에 즉시(eager) 로딩해 추가 SELECT 를 없앤다. → 1쿼리.
     *   - labOrder 는 @JoinColumn(nullable = false) 라 inner join fetch 로도 누락 행이 없다.
     *     (nullable 이면 LEFT join fetch 를 써야 함)
     *
     * ⚠ 진행 상태(일정/검체/판정)는 여기서 join 하지 않는다.
     *   SPECIMEN 은 접수와 1:N 이라 left join 을 걸면 검체 3건인 접수가 3행으로 늘어난다.
     *   Service 가 접수 목록을 먼저 뽑은 뒤 IN 절로 일괄 조회해 붙인다.
     *   (LabWorklistService 참고 — 접수가 몇 건이든 쿼리 수는 항상 일정하다)
     */
    @Query("""
            select r from LabReceptionEntity r
            join fetch r.labOrder
            where r.receptionStatusCode = :receptionStatusCode
            order by r.createdAt asc
            """)
    List<LabReceptionEntity> findWorklistByStatus(String receptionStatusCode);

    /** 제외 여부와 무관한 전체 워크리스트. 정렬 기준은 findWorklistByStatus 와 같다. */
    @Query("""
            select r from LabReceptionEntity r
            join fetch r.labOrder
            order by r.createdAt asc
            """)
    List<LabReceptionEntity> findWorklistAll();
}
