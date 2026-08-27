package kr.co.seoulit.his.labimagingservice.laborder.entity;

import jakarta.persistence.*;
import kr.co.seoulit.his.labimagingservice.common.entity.BaseAuditEntity;
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

    @Column(name = "received_by_id", length = 20, nullable = false)
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

    /** 워크리스트로 되돌린다. 제외 기록은 지운다. */
    public void restore() {
        this.receptionStatusCode = ReceptionStatus.ACCEPTED.name();
        this.exclusionReason = null;
        this.excludedAt = null;
    }
    void assignLabOrder(LabOrderEntity labOrder) {
        this.labOrder = labOrder;
    }
}
