package kr.co.seoulit.his.labimagingservice.labspecimen.repository;

import kr.co.seoulit.his.labimagingservice.labspecimen.entity.LabTestSpecimenRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * 검사별 허용 검체·검체용기 기준 조회. 읽기 전용(마스터) — 6차.
 */
public interface LabTestSpecimenRuleRepository extends JpaRepository<LabTestSpecimenRuleEntity, String> {

    /**
     * 여러 검사항목코드의 사용중(use_yn='Y') 규칙을 한 번에 조회한다.
     * ⚠ 접수에 검사항목이 여러 개면 그 규칙들의 "합집합"이 허용 조합이다(2-1). 호출한 쪽이 합친다.
     */
    List<LabTestSpecimenRuleEntity> findByTestTypeCodeInAndUseYn(Collection<String> testTypeCodes, String useYn);
}
