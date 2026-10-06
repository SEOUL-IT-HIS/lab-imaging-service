package kr.co.seoulit.his.labimagingservice.laborder.entity;

import jakarta.persistence.*;
import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.entity.BaseAuditEntity;
import kr.co.seoulit.his.labimagingservice.common.exception.LabImagingBusinessException;
import kr.co.seoulit.his.labimagingservice.common.status.CancelOutcome;
import kr.co.seoulit.his.labimagingservice.common.status.ReceptionStatus;
import kr.co.seoulit.his.labimagingservice.labschedule.entity.LabScheduleEntity;
import kr.co.seoulit.his.labimagingservice.labspecimen.entity.SpecimenEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 검사접수 (LAB_RECEPTION)
 * LAB_ORDER : LAB_RECEPTION = 1:N (2026-07-13 결정, 오더 1건에 접수 여러 건 허용)
 */
@Entity
@Table(name = "LAB_RECEPTION")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LabReceptionEntity extends BaseAuditEntity {

    @Id
    @Column(name = "lab_reception_id", length = 36, nullable = false, updatable = false)
    private String labReceptionId;

    @Column(name = "reception_no", length = 20, nullable = false, unique = true)
    private String receptionNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lab_order_id", nullable = false)
    private LabOrderEntity labOrder;

    /**
     * 화면 표시용 업무번호. 검증·참조에는 쓰지 않는다.
     *
     * ⚠ nullable 이다. 환자번호를 발급하는 주체가 아직 없어서 값이 없는 접수가 존재한다.
     *   (2026-08-25 결정 — 처방코어도 이 값을 갖고 있지 않고, patient-service 응답에도 없다)
     *   화면 표시용일 뿐 식별·검증에는 쓰지 않으므로 없어도 업무는 진행된다. 식별은 patient_id 로 한다.
     *   발급 주체가 정해지면 NOT NULL 로 되돌린다.
     */
    @Column(name = "patient_no", length = 20)
    private String patientNo;

    /** patient-service 내부 식별자. 참조/검증(API 호출)은 이 값을 쓴다. */
    @Column(name = "patient_id", length = 36)
    private String patientId;

    @Column(name = "reception_status_code", length = 10, nullable = false)
    private String receptionStatusCode;

    @Column(name = "urgency_yn", columnDefinition = "CHAR(1)", nullable = false)
    private String urgencyYn;

    @Column(name = "received_by_id", length = 36, nullable = false)
    private String receivedById;

    @Column(name = "ack_sent_yn", columnDefinition = "CHAR(1)", nullable = false)
    private String ackSentYn;

    @Column(name = "ack_sent_at")
    private LocalDateTime ackSentAt;

    /**
     * 워크리스트 제외 사유. 상태가 EXCLUDED 일 때만 값이 있다.
     *
     * ⚠ 사유를 남기는 이유 — 담당자가 바뀌어도 "왜 뺐는지"를 검증할 수 있어야 한다.
     *   기간 조건으로 자동으로 빼는 방식 대신 담당자 판단으로 빼기로 한 것이,
     *   바로 이 기록이 남기 때문이다. 사유가 없으면 그 장점이 사라진다.
     *
     * ⚠ 공통코드가 아니라 자유 텍스트다. 어떤 사유가 실제로 쓰이는지는 운영해봐야 알 수 있어,
     *   당분간 모아본 뒤 코드화 여부를 판단한다.
     */
    @Column(name = "exclusion_reason", length = 200)
    private String exclusionReason;

    /** 제외 처리 일시. 상태가 EXCLUDED 일 때만 값이 있다. */
    @Column(name = "excluded_at")
    private LocalDateTime excludedAt;

    /**
     * 처방 취소 요청을 받은 일시. (05번 지시서 Phase 1-B, 2026-10-06)
     * ⚠ 거절된 건도 채운다 — "요청은 받았으나 막았다"를 검사실이 알 수 있어야 한다.
     */
    @Column(name = "cancel_requested_at")
    private LocalDateTime cancelRequestedAt;

    /** 처방의 취소 사유(OPD cancelReason). 200자 초과 시 잘라서 저장한다(서비스 책임). */
    @Column(name = "cancel_reason", length = 200)
    private String cancelReason;

    /** 취소한 사용자ID(OPD cancelledBy). 표시·기록용 — 직원 검증은 하지 않는다. */
    @Column(name = "cancelled_by_id", length = 36)
    private String cancelledById;

    /** 취소 처리 결과: CANCELLED(전체 취소)/PARTIAL(일부만 취소)/REFUSED(전부 거절). */
    @Column(name = "cancel_outcome", length = 10)
    private String cancelOutcome;

    @OneToMany(mappedBy = "labReception", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<LabScheduleEntity> schedules = new ArrayList<>();

    @OneToMany(mappedBy = "labReception", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<SpecimenEntity> specimens = new ArrayList<>();

    @Builder
    public LabReceptionEntity(String receptionNo, String patientNo, String patientId, String receptionStatusCode,
                               String urgencyYn, String receivedById, String ackSentYn, LocalDateTime ackSentAt) {
        this.receptionNo = receptionNo;
        this.patientNo = patientNo;
        this.patientId = patientId;
        this.receptionStatusCode = receptionStatusCode;
        this.urgencyYn = urgencyYn;
        this.receivedById = receivedById;
        this.ackSentYn = ackSentYn;
        this.ackSentAt = ackSentAt;
    }

    @PrePersist
    private void generateId() {
        if (this.labReceptionId == null) {
            this.labReceptionId = UUID.randomUUID().toString();
        }
    }
    /**
     * 워크리스트에서 제외한다. (담당자가 처리하지 않기로 판단한 건)
     *
     * ⚠ 상태 변경을 setter 가 아니라 의미 있는 메서드로 열어 둔다.
     *   setReceptionStatusCode 를 열어두면 사유 없이 상태만 바꾸는 코드가 생길 수 있는데,
     *   그러면 "왜 뺐는지"가 비어 있는 행이 남는다. 세 값을 항상 함께 바꾸도록 묶는다.
     */
    public void exclude(String exclusionReason, LocalDateTime excludedAt) {
        this.receptionStatusCode = ReceptionStatus.EXCLUDED.name();
        this.exclusionReason = exclusionReason;
        this.excludedAt = excludedAt;
    }

    /**
     * 워크리스트로 되돌린다. 제외 기록은 지운다.
     * ⚠ CANCELLED 는 이 메서드로 되돌릴 수 없다 — 호출하는 쪽(LabOrderService.restoreReception)이
     *   상태를 먼저 확인해 LAB121 로 거절한다(05번 지시서 Phase 3). 여기서는 EXCLUDED 를
     *   전제로 하는 필드만 지운다.
     */
    public void restore() {
        this.receptionStatusCode = ReceptionStatus.ACCEPTED.name();
        this.exclusionReason = null;
        this.excludedAt = null;
    }

    /**
     * 처방 취소 요청의 기록을 남긴다. 성공(outcome=CANCELLED)이든 거절(REFUSED/PARTIAL)이든
     * 항상 호출한다. (05번 지시서 Phase 1-B)
     *
     * ⚠ 상태 전이는 이 메서드가 하지 않는다. 취소가 실제로 성공했을 때만 cancel() 을 별도로
     *   부른다 — 거절돼도(REFUSED/PARTIAL) 접수 상태는 그대로 두고 이 기록만 남겨
     *   검사실이 워크리스트에서 경고 배지로 볼 수 있게 한다(LabOrderCancelService 참고).
     */
    public void markCancelRequest(LocalDateTime requestedAt, String reason, String byId, CancelOutcome outcome) {
        this.cancelRequestedAt = requestedAt;
        this.cancelReason = reason;
        this.cancelledById = byId;
        this.cancelOutcome = outcome.name();
    }

    /** 접수를 취소 상태로 전환한다. 오더의 모든 항목이 취소됐을 때만 호출한다. */
    public void cancel() {
        this.receptionStatusCode = ReceptionStatus.CANCELLED.name();
    }

    /** 취소된 접수인지. */
    public boolean isCancelled() {
        return ReceptionStatus.CANCELLED.name().equals(this.receptionStatusCode);
    }

    /**
     * 취소된 접수면 거절한다(LAB121). (05번 지시서 Phase 3)
     *
     * ⚠ 보통 "서비스가 예외를 던지고 엔티티는 상태만 들고 있는다"는 이 코드베이스의 관례에서
     *   벗어난다. 일정·검체·인수·결과 등 접수를 읽는 지점이 7곳 넘게 흩어져 있어, 조건문을
     *   그 수만큼 복붙하면 "취소 판단 기준"이 slowly 갈라진다. 지시서가 명시적으로 요구한
     *   한 줄 공통 가드다 — 기준을 바꿀 일이 생기면 이 메서드 하나만 고치면 된다.
     */
    public void requireNotCancelled() {
        if (isCancelled()) {
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB121, "취소된 접수입니다. (receptionNo=" + this.receptionNo + ")");
        }
    }

    void assignLabOrder(LabOrderEntity labOrder) {
        this.labOrder = labOrder;
    }
}
