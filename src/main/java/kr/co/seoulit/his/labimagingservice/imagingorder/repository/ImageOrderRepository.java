package kr.co.seoulit.his.labimagingservice.imagingorder.repository;

import kr.co.seoulit.his.labimagingservice.imagingorder.entity.ImageOrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ImageOrderRepository extends JpaRepository<ImageOrderEntity, String> {

    Optional<ImageOrderEntity> findByImageOrderNo(String imageOrderNo);

    boolean existsByImageOrderNo(String imageOrderNo);
}
