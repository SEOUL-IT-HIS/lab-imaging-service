package kr.co.seoulit.his.labimagingservice.labresult.repository;

import kr.co.seoulit.his.labimagingservice.labresult.entity.LabResultDetailEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * 검사결과 상세(결과항목) 조회. 6차.
 *
 * ⚠ 단건 조회(결과 1건의 상세)는 이 리포지토리를 쓰지 않는다. LabResultEntity.details 가
 *   이미 그 결과의 상세 컬렉션이라 JPA 지연로딩으로 충분하다. 이 리포지토리는 "여러 결과의
 *   상세를 한 번에" 조회해 N+1 을 막을 때만 쓴다(접수의 검사항목 목록 조회, 2-4).
 */
public interface LabResultDetailRepository extends JpaRepository<LabResultDetailEntity, String> {

    List<LabResultDetailEntity> findByLabResult_LabResultIdInOrderByDetailSeqAsc(Collection<String> labResultIds);
}
