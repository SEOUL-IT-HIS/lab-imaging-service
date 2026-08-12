package kr.co.seoulit.his.labimagingservice.laborder.repository;

import kr.co.seoulit.his.labimagingservice.laborder.entity.LabReceptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface LabReceptionRepository extends JpaRepository<LabReceptionEntity, String> {

    Optional<LabReceptionEntity> findByReceptionNo(String receptionNo);

    /**
     * 일정등록 대상(미일정) 검사접수 목록 조회. 최신 접수가 위로 오도록 created_at 내림차순.
     *
     * ── 미일정 필터: "not exists (latest_yn='Y' 스케줄)"
     *   - 재등록 로직상 한 접수의 유효 일정은 latest_yn='Y' 딱 1건뿐이다(조건부 UNIQUE 로 보장).
     *     따라서 "latest_yn='Y' 인 LAB_SCHEDULE 이 하나도 없는 접수" = 아직 일정을 안 잡은 접수다.
     *   - 이미 일정이 잡힌 접수는 빠지므로, 화면에는 "일정 등록 대상"만 남는다.
     *
     * ── N+1 방어의 핵심: "join fetch r.labOrder"
     *   - LabReceptionEntity.labOrder 는 @ManyToOne(FetchType.LAZY) 다. 그냥 목록만 가져온 뒤
     *     매핑에서 각 건의 getLabOrder() 를 건드리면 그 순간마다 SELECT 가 1번씩 추가로 나간다.
     *     → 목록 1번 + 행마다 N번 = 총 N+1 쿼리 (접수 20건이면 21쿼리). 행이 늘수록 급격히 느려진다.
     *   - "join fetch" 는 labOrder 를 목록 쿼리와 "한 방"에 즉시(eager) 로딩해 추가 SELECT 를 없앤다. → 1쿼리.
     *   - labOrder 는 @JoinColumn(nullable = false) 라 inner join fetch 로도 누락 행이 없다.
     *     (nullable 이면 LEFT join fetch 를 써야 함)
     *   ※ not exists 서브쿼리는 스케줄의 "존재 여부"만 판단할 뿐 스케줄을 select/로딩 하지 않으므로,
     *      스케줄 쪽에서 별도 N+1 이 생기지 않는다.
     */
    @Query("""
            select r from LabReceptionEntity r
            join fetch r.labOrder
            where not exists (
                select 1 from LabScheduleEntity s
                 where s.labReception = r and s.latestYn = 'Y')
            order by r.createdAt desc
            """)
    List<LabReceptionEntity> findUnscheduledWithLabOrder();

    /**
     * 일정이 이미 등록된 검사접수 목록. (재조정 대상)
     * findUnscheduledWithLabOrder 와 not exists / exists 만 다르다.
     *
     * ⚠ exists/not exists 를 파라미터로 뒤집는 JPQL 은 읽기 어려워져, 메서드를 나누고
     *   Service 에서 분기한다.
     */
    @Query("""
            select r from LabReceptionEntity r
            join fetch r.labOrder
            where exists (
                select 1 from LabScheduleEntity s
                 where s.labReception = r and s.latestYn = 'Y')
            order by r.createdAt desc
            """)
    List<LabReceptionEntity> findScheduledWithLabOrder();

    /** 일정 등록 여부와 무관한 전체 검사접수 목록. */
    @Query("""
            select r from LabReceptionEntity r
            join fetch r.labOrder
            order by r.createdAt desc
            """)
    List<LabReceptionEntity> findAllWithLabOrder();
}
