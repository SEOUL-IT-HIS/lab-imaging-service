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
}
