package kr.co.seoulit.his.labimagingservice.imagingschedule.repository;

import kr.co.seoulit.his.labimagingservice.imagingschedule.entity.ImageScheduleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ImageScheduleRepository extends JpaRepository<ImageScheduleEntity, String> {
    Optional<ImageScheduleEntity> findByImageReception_ImageReceptionIdAndLatestYn(String imageReceptionId, String latestYn);

    /**
     * 여러 접수의 최종 일정을 한 번에 조회한다. (목록 화면에서 예정일시를 함께 보여주기 위함)
     *
     * ⚠ 접수 건마다 단건 조회를 부르면 행 수만큼 쿼리가 나간다(N+1).
     *   접수ID 목록을 통째로 넘겨 IN 절 한 번으로 끝낸다.
     */
    List<ImageScheduleEntity> findByImageReception_ImageReceptionIdInAndLatestYn(
            Collection<String> imageReceptionIds, String latestYn);
}
