package kr.co.seoulit.his.labimagingservice.imagingorder.repository;

import kr.co.seoulit.his.labimagingservice.imagingorder.entity.ImageReceptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ImageReceptionRepository extends JpaRepository<ImageReceptionEntity, String> {

    Optional<ImageReceptionEntity> findByReceptionNo(String receptionNo);
}
