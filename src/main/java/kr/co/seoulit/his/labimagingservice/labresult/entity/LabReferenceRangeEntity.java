package kr.co.seoulit.his.labimagingservice.labresult.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 결과항목별 참고범위 (LAB_REFERENCE_RANGE, 성별 구분). 읽기 전용 — 6차 (2026-09-30)
 *
 * ⚠ sex_code: "ALL"=공통, "01"=남성, "02"=여성 (환자 서비스 genderCd 기준 — 03 미상·04 기타는
 *   행이 없다. 그 경우 판정 규칙은 LabResultService 참고: ALL 행이 없으면 판정하지 않는다).
 * ⚠ 관리 화면 없음(읽기 전용) — LabTestSpecimenRuleEntity 와 같은 성격.
 */
@Entity
@Table(name = "LAB_REFERENCE_RANGE")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LabReferenceRangeEntity {

    @Id
    @Column(name = "reference_range_id", length = 36, nullable = false, updatable = false)
    private String referenceRangeId;

    /** 결과항목코드 (공통코드 RESULT_ITEM_CD) */
    @Column(name = "result_item_code", length = 20, nullable = false)
    private String resultItemCode;

    /** ALL / 01(남성) / 02(여성) */
    @Column(name = "sex_code", length = 3, nullable = false)
    private String sexCode;

    /** 참고범위 (수치 "최소-최대" 또는 정성 "음성,정상") */
    @Column(name = "reference_range", length = 50, nullable = false)
    private String referenceRange;

    @Column(name = "use_yn", columnDefinition = "CHAR(1)", nullable = false)
    private String useYn;
}
