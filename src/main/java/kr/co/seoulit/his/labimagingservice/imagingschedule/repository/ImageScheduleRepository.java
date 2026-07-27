package kr.co.seoulit.his.labimagingservice.imagingschedule.repository;

import kr.co.seoulit.his.labimagingservice.imagingschedule.entity.ImageScheduleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ImageScheduleRepository extends JpaRepository<ImageScheduleEntity, String> {
    Optional<ImageScheduleEntity> findByImageReception_ImageReceptionIdAndLatestYn(String imageReceptionId, String latestYn);
}
