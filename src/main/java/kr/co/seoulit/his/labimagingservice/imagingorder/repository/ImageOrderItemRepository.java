package kr.co.seoulit.his.labimagingservice.imagingorder.repository;

import kr.co.seoulit.his.labimagingservice.imagingorder.entity.ImageOrderItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImageOrderItemRepository extends JpaRepository<ImageOrderItemEntity, String> {
}
