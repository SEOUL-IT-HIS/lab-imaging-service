package kr.co.seoulit.his.labimagingservice.imagingorder.repository;

import kr.co.seoulit.his.labimagingservice.imagingorder.entity.ImageReceptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ImageReceptionRepository extends JpaRepository<ImageReceptionEntity, String> {

    Optional<ImageReceptionEntity> findByReceptionNo(String receptionNo);

    @Query("""
            select r from ImageReceptionEntity r
            join fetch r.imageOrder
            where not exists (
                select 1 from ImageScheduleEntity s
                 where s.imageReception = r and s.latestYn = 'Y')
            order by r.createdAt desc
            """)
    List<ImageReceptionEntity> findUnscheduledWithImageOrder();

    /**
     * 일정이 이미 등록된 영상접수 목록. (재조정 대상)
     * findUnscheduledWithImageOrder 와 not exists / exists 만 다르다.
     *
     * ⚠ exists/not exists 를 파라미터로 뒤집는 JPQL 은 읽기 어려워져, 메서드를 나누고
     *   Service 에서 분기한다.
     */
    @Query("""
            select r from ImageReceptionEntity r
            join fetch r.imageOrder
            where exists (
                select 1 from ImageScheduleEntity s
                 where s.imageReception = r and s.latestYn = 'Y')
            order by r.createdAt desc
            """)
    List<ImageReceptionEntity> findScheduledWithImageOrder();

    /**
     * 워크리스트 — 접수상태로 거른 목록. (ACCEPTED=처리 대상, EXCLUDED=제외됨)
     *
     * ⚠ 정렬이 위 목록들과 반대(asc)다. 실수가 아니다.
     *   워크리스트는 오래 대기한 건이 위로 와야 처리 순서가 드러난다.
     *   접수 목록 화면은 최근 접수를 먼저 보는 화면이라 desc 다. (검사 쪽과 동일)
     *
     * ⚠ 진행 상태(일정/동의/촬영)는 여기서 join 하지 않는다.
     *   CONSENT 도 IMAGE_FILE 도 접수와 1:N 이라 left join 을 걸면 행이 곱해진다.
     *   Service 가 접수 목록을 먼저 뽑은 뒤 IN 절로 일괄 조회해 붙인다.
     */
    @Query("""
            select r from ImageReceptionEntity r
            join fetch r.imageOrder
            where r.receptionStatusCode = :receptionStatusCode
            order by r.createdAt asc
            """)
    List<ImageReceptionEntity> findWorklistByStatus(String receptionStatusCode);

    /** 제외 여부와 무관한 전체 워크리스트. 정렬 기준은 findWorklistByStatus 와 같다. */
    @Query("""
            select r from ImageReceptionEntity r
            join fetch r.imageOrder
            order by r.createdAt asc
            """)
    List<ImageReceptionEntity> findWorklistAll();

    /** 일정 등록 여부와 무관한 전체 영상접수 목록. */
    @Query("""
            select r from ImageReceptionEntity r
            join fetch r.imageOrder
            order by r.createdAt desc
            """)
    List<ImageReceptionEntity> findAllWithImageOrder();
}
