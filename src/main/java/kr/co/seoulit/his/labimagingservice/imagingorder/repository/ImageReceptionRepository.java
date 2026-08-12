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

    /** 일정 등록 여부와 무관한 전체 영상접수 목록. */
    @Query("""
            select r from ImageReceptionEntity r
            join fetch r.imageOrder
            order by r.createdAt desc
            """)
    List<ImageReceptionEntity> findAllWithImageOrder();
}
