package kr.co.seoulit.his.labimagingservice.labresult.entity;

import jakarta.persistence.*;
import kr.co.seoulit.his.labimagingservice.common.entity.BaseAuditEntity;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabOrderItemEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 일반검사 결과 (LAB_RESULT)
 * 대응 유스케이스: UC-RST-01 일반검사결과등록 (Jira ZP2-13)
 *
 * ⚠ 검사항목(LAB_ORDER_ITEM) 1건에 결과 1건이다(1:1). lab_order_item_id 에 UNIQUE 가 걸려 있다.
 *   검사 1회에 대한 결과는 하나뿐이고, 다시 검사하면 오더 항목부터 새로 생기기 때문이다.
 *   (SPECIMEN : SPECIMEN_ACCEPTANCE 와 같은 구조 — 그쪽 엔티티를 그대로 따랐다)
 *
 * ⚠ 장비 연동으로 결과를 수신하는 경로는 없다. 수기 입력만 있다. (2026-08-31 범위 결정)
 *   그래서 "누가 언제 입력했는가"(recorded_by_id / recorded_at)가 결과의 유일한 출처 기록이다.
 *
 * ⚠ 상태는 등록(01) → 확정(02) 두 단계뿐이고 되돌아가지 않는다.
 *   확정 시점을 confirmed_at / confirmed_by_id 로 남긴다. 별도 이력 테이블은 없다.
 *   (해석 근거는 LabResultService 클래스 주석의 "가정" 항목 참고)
 */
@Entity
@Table(name = "LAB_RESULT")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LabResultEntity extends BaseAuditEntity {

    @Id
    @Column(name = "lab_result_id", length = 36, nullable = false, updatable = false)
    private String labResultId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lab_order_item_id", nullable = false, unique = true)
    private LabOrderItemEntity labOrderItem;

    /**
     * ⚠ 숫자가 아니라 문자열이다. 정성검사 결과("음성", "Positive")가 같은 컬럼에 들어온다.
     *   숫자로 잡으면 정성 결과를 담을 수 없고, 유효숫자(0.50 vs 0.5)도 입력한 그대로 못 남긴다.
     */
    @Column(name = "result_value", length = 200, nullable = false)
    private String resultValue;

    @Column(name = "result_unit", length = 20)
    private String resultUnit;

    /**
     * 참고범위 — "정상으로 보는 값"을 담는다. 정량은 "3.5-5.5", 정성은 "음성" 형태다.
     *
     * ⚠ 검사항목별 기준값 마스터가 아직 없어서 입력자가 직접 넣는다.
     *   원래는 항목코드로 마스터에서 끌어와야 하고, 4차 스프린트 체크리스트에도
     *   "검사 항목별 정상 범위 데이터가 없다"로 이미 적혀 있다.
     *   TODO(마스터 확보 후): lab_item_code 로 조회해 서버가 채우고 이 입력값은 받지 않는다.
     */
    @Column(name = "reference_range", length = 50)
    private String referenceRange;

    /**
     * ⚠ 요청값을 그대로 받지 않는다. 참고범위와 결과값을 비교해 서버가 계산한다. (ZP2-99)
     *   입력자가 직접 정하게 두면 같은 수치가 사람마다 다르게 분류된다.
     *   판정 규칙은 LabResultService.decideAbnormalYn 참고.
     */
    @Column(name = "abnormal_yn", columnDefinition = "CHAR(1)", nullable = false)
    private String abnormalYn;

    /** 공통코드 RESULT_STATUS_CD — 01=등록, 02=확정. 요청값이 아니라 서비스가 전이시킨다. */
    @Column(name = "result_status_code", length = 10, nullable = false)
    private String resultStatusCode;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    @Column(name = "recorded_by_id", length = 20, nullable = false)
    private String recordedById;

    /** 확정 전에는 비어 있다. 확정과 동시에 둘 다 채워진다. */
    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "confirmed_by_id", length = 20)
    private String confirmedById;

    @Builder
    public LabResultEntity(String resultValue, String resultUnit, String referenceRange,
                           String abnormalYn, String resultStatusCode,
                           LocalDateTime recordedAt, String recordedById) {
        this.resultValue = resultValue;
        this.resultUnit = resultUnit;
        this.referenceRange = referenceRange;
        this.abnormalYn = abnormalYn;
        this.resultStatusCode = resultStatusCode;
        this.recordedAt = recordedAt;
        this.recordedById = recordedById;
    }

    @PrePersist
    private void generateId() {
        if (this.labResultId == null) {
            this.labResultId = UUID.randomUUID().toString();
        }
    }

    public void assignLabOrderItem(LabOrderItemEntity labOrderItem) {
        this.labOrderItem = labOrderItem;
    }

    /**
     * 결과값을 고쳐 쓴다. (확정 전에만 호출된다 — 호출 전에 서비스가 상태를 확인한다)
     *
     * ⚠ 상태·확정정보·기록자는 건드리지 않는다. 수정은 "누가 처음 입력했는가"를 바꾸는 행위가 아니다.
     * ⚠ abnormalYn 은 참고범위가 함께 바뀔 수 있으므로 서비스가 다시 계산해 넘긴다.
     */
    public void modifyResult(String resultValue, String resultUnit,
                             String referenceRange, String abnormalYn) {
        this.resultValue = resultValue;
        this.resultUnit = resultUnit;
        this.referenceRange = referenceRange;
        this.abnormalYn = abnormalYn;
    }

    /**
     * 등록(01) → 확정(02) 으로 전이한다.
     *
     * ⚠ 상태 전이와 확정정보 기록을 한 메서드에 둔다. 따로 두면 상태만 바뀌고
     *   confirmed_at 이 빈 행이 생길 수 있다. 둘은 항상 함께 움직여야 한다.
     */
    public void confirm(String resultStatusCode, String confirmedById, LocalDateTime confirmedAt) {
        this.resultStatusCode = resultStatusCode;
        this.confirmedById = confirmedById;
        this.confirmedAt = confirmedAt;
    }
}
