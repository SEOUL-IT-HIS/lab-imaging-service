package kr.co.seoulit.his.labimagingservice.labschedule.repository;

import kr.co.seoulit.his.labimagingservice.labschedule.entity.LabScheduleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface LabScheduleRepository extends JpaRepository<LabScheduleEntity, String> {
    Optional<LabScheduleEntity> findByLabReception_LabReceptionIdAndLatestYn(String labReceptionId, String latestYn);

}
