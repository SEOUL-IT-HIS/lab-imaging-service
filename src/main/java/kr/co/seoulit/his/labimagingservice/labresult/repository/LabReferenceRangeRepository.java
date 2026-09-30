package kr.co.seoulit.his.labimagingservice.labresult.repository;

import kr.co.seoulit.his.labimagingservice.labresult.entity.LabReferenceRangeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 결과항목별 참고범위(성별 구분) 조회. 읽기 전용(마스터) — 6차.
 */
public interface LabReferenceRangeRepository extends JpaRepository<LabReferenceRangeEntity, String> {

    /** 적용 순서(2-2): 환자 성별에 맞는 행 → ALL 행 → 없음. 호출한 쪽이 이 순서로 조회·선택한다. */
    Optional<LabReferenceRangeEntity> findByResultItemCodeAndSexCodeAndUseYn(
            String resultItemCode, String sexCode, String useYn);

    /**
     * 여러 결과항목의 참고범위를 한 번에 조회한다. (접수 화면의 entryItems 조립용, N+1 방지)
     * 성별 적용 로직은 호출한 쪽이 결과항목코드별로 ALL/성별 행을 골라 처리한다.
     */
    List<LabReferenceRangeEntity> findByResultItemCodeInAndUseYn(Collection<String> resultItemCodes, String useYn);
}
