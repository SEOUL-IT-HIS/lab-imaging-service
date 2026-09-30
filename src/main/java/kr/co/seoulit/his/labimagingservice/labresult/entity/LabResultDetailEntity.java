package kr.co.seoulit.his.labimagingservice.labresult.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import kr.co.seoulit.his.labimagingservice.common.entity.BaseAuditEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 검사결과 상세(결과항목) (LAB_RESULT_DETAIL). 6차 — 일반검사 결과항목(상세) (2026-09-30)
 *
 * ⚠ LAB_RESULT 의 자식(1:N, cascade ALL + orphanRemoval)이다. 미생물 감수성
 *   (MicrobiologyResultEntity.susceptibilities) 과 같은 "통째로 교체" 패턴을 쓴다 —
 *   수정 시 기존 목록을 clear() 하고 새 목록을 다시 담는다. UNIQUE(lab_result_id, detail_seq) /
 *   UNIQUE(lab_result_id, result_item_code) 제약이 있지만, Hibernate 가 orphanRemoval 삭제를
 *   insert 보다 먼저 flush 하므로(자식 컬렉션의 기존 관리 방식과 동일) 위반이 나지 않는다.
 *
 * ⚠ result_unit / reference_range 는 "판정에 적용된 값"을 저장한다(요청값이 아니라 서버가 정한 값,
 *   LabReferenceRangeEntity 에서 가져온 것). 그 시점의 판정 근거를 남겨야 나중에 "왜 그때 이상으로
 *   판정했는지"를 재구성할 수 있다 — 참고범위가 나중에 바뀌어도 이 행은 바뀌지 않는다.
 */
@Entity
@Table(name = "LAB_RESULT_DETAIL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LabResultDetailEntity extends BaseAuditEntity {

    @Id
    @Column(name = "lab_result_detail_id", length = 36, nullable = false, updatable = false)
    private String labResultDetailId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lab_result_id", nullable = false)
    private LabResultEntity labResult;

    /** 표시 순번 (1부터, 검사별 항목 순서) */
    @Column(name = "detail_seq", nullable = false)
    private Integer detailSeq;

    /** 결과항목코드 (공통코드 RESULT_ITEM_CD) */
    @Column(name = "result_item_code", length = 20, nullable = false)
    private String resultItemCode;

    @Column(name = "result_value", length = 200, nullable = false)
    private String resultValue;

    /** 판정에 적용된 단위 (LabResultItemRuleEntity.defaultUnit 또는 요청 대체값) */
    @Column(name = "result_unit", length = 20)
    private String resultUnit;

    /** 판정에 적용된 참고범위 (LabReferenceRangeEntity 또는 요청 대체값) */
    @Column(name = "reference_range", length = 50)
    private String referenceRange;

    @Column(name = "abnormal_yn", columnDefinition = "CHAR(1)", nullable = false)
    private String abnormalYn;

    @Builder
    public LabResultDetailEntity(Integer detailSeq, String resultItemCode, String resultValue,
                                 String resultUnit, String referenceRange, String abnormalYn) {
        this.detailSeq = detailSeq;
        this.resultItemCode = resultItemCode;
        this.resultValue = resultValue;
        this.resultUnit = resultUnit;
        this.referenceRange = referenceRange;
        this.abnormalYn = abnormalYn;
    }

    @PrePersist
    private void generateId() {
        if (this.labResultDetailId == null) {
            this.labResultDetailId = UUID.randomUUID().toString();
        }
    }

    void assignLabResult(LabResultEntity labResult) {
        this.labResult = labResult;
    }
}
