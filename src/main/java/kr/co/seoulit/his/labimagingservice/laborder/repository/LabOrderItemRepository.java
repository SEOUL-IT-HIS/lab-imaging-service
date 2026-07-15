package kr.co.seoulit.his.labimagingservice.laborder.repository;

import kr.co.seoulit.his.labimagingservice.laborder.entity.LabOrderItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabOrderItemRepository extends JpaRepository<LabOrderItemEntity, String> {
}
