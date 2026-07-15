package kr.co.seoulit.his.labimagingservice.laborder.repository;

import kr.co.seoulit.his.labimagingservice.laborder.entity.LabReceptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LabReceptionRepository extends JpaRepository<LabReceptionEntity, String> {

    Optional<LabReceptionEntity> findByReceptionNo(String receptionNo);
}
