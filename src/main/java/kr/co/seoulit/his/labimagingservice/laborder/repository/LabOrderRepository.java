package kr.co.seoulit.his.labimagingservice.laborder.repository;

import kr.co.seoulit.his.labimagingservice.laborder.entity.LabOrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LabOrderRepository extends JpaRepository<LabOrderEntity, String> {

    Optional<LabOrderEntity> findByLabOrderNo(String labOrderNo);

    boolean existsByLabOrderNo(String labOrderNo);
}
