package kr.co.seoulit.his.labimagingservice.labresult.repository;

import kr.co.seoulit.his.labimagingservice.labresult.entity.LabResultItemRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * 검사별 결과항목 기준 조회. 읽기 전용(마스터) — 6차.
 */
public interface LabResultItemRuleRepository extends JpaRepository<LabResultItemRuleEntity, String> {

    /**
     * 이 검사의 사용중(use_yn='Y') 결과항목 규칙, 순번 순.
     * ⚠ "결과항목 방식"의 판정 기준 자체가 이 목록이 비어 있지 않은가다(2-2 "해당 검사에
     *   LAB_RESULT_ITEM_RULE(use_yn='Y') 행이 있는 경우"). 비어 있으면 기존 방식이다.
     */
    List<LabResultItemRuleEntity> findByTestTypeCodeAndUseYnOrderByItemSeqAsc(String testTypeCode, String useYn);

    /** 여러 검사의 규칙을 한 번에 조회한다. (접수의 검사항목 목록 조회, N+1 방지 — 2-4) */
    List<LabResultItemRuleEntity> findByTestTypeCodeInAndUseYnOrderByTestTypeCodeAscItemSeqAsc(
            Collection<String> testTypeCodes, String useYn);
}
