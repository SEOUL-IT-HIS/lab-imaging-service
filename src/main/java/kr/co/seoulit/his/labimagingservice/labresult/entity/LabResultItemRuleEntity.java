package kr.co.seoulit.his.labimagingservice.labresult.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 검사별 결과항목 기준 (LAB_RESULT_ITEM_RULE). 읽기 전용 — 6차 (2026-09-30)
 *
 * ⚠ 관리 화면은 이번 범위가 아니다(LabTestSpecimenRuleEntity 와 같은 성격). SELECT 전용이라
 *   @Builder·수정 메서드가 없다.
 * ⚠ 이 테이블에 test_type_code(검사) 행이 하나도 없으면 "기존 방식"(결과항목 없이 LAB_RESULT
 *   자체에 값을 담는 방식)이다 — LabResultService 가 이 존재 여부로 두 방식을 가른다.
 */
@Entity
@Table(name = "LAB_RESULT_ITEM_RULE")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LabResultItemRuleEntity {

    @Id
    @Column(name = "rule_id", length = 36, nullable = false, updatable = false)
    private String ruleId;

    /** 검사항목코드 (공통코드 TEST_TYPE_CD) */
    @Column(name = "test_type_code", length = 20, nullable = false)
    private String testTypeCode;

    /** 결과항목코드 (공통코드 RESULT_ITEM_CD) */
    @Column(name = "result_item_code", length = 20, nullable = false)
    private String resultItemCode;

    /** 화면 표시 순번 */
    @Column(name = "item_seq", nullable = false)
    private Integer itemSeq;

    @Column(name = "default_unit", length = 20)
    private String defaultUnit;

    @Column(name = "use_yn", columnDefinition = "CHAR(1)", nullable = false)
    private String useYn;
}
