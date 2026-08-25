package kr.co.seoulit.his.labimagingservice.laborder.entity;

import kr.co.seoulit.his.labimagingservice.common.entity.BaseAuditEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 검사오더 (LAB_ORDER)
 * 대응 유스케이스: UC-SPC-01 검사오더접수
 */
@Entity
@Table(name = "LAB_ORDER")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LabOrderEntity extends BaseAuditEntity {

    @Id
    @Column(name = "lab_order_id", length = 36, nullable = false, updatable = false)
    private String labOrderId;

    // MSA 간 참조 식별자를 VARCHAR2(36)으로 통일 (2026-08-25). 처방코어의 prescriptionId 가 최대 36자.
    @Column(name = "lab_order_no", length = 36, nullable = false, unique = true)
    private String labOrderNo;

    @Column(name = "system_code", length = 10, nullable = false)
    private String systemCode;

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

    /** 화면 표시용 업무번호. */
    @Column(name = "physician_no", length = 20)
    private String physicianNo;

    /** 처방의 내부 식별자. 참조용. */
    @Column(name = "physician_id", length = 36)
    private String physicianId;

    @Column(name = "treat_type_code", length = 10, nullable = false)
    private String treatTypeCode;

    @Column(name = "urgency_yn", columnDefinition = "CHAR(1)", nullable = false)
    private String urgencyYn;

    @Column(name = "order_status_code", length = 10, nullable = false)
    private String orderStatusCode;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @OneToMany(mappedBy = "labOrder", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<LabOrderItemEntity> orderItems = new ArrayList<>();

    @OneToMany(mappedBy = "labOrder", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<LabReceptionEntity> receptions = new ArrayList<>();

    @Builder
    public LabOrderEntity(String labOrderNo, String systemCode, String patientNo, String patientId,
                           String physicianNo, String physicianId,
                           String treatTypeCode, String urgencyYn, String orderStatusCode, LocalDateTime receivedAt) {
        this.labOrderNo = labOrderNo;
        this.systemCode = systemCode;
        this.patientNo = patientNo;
        this.patientId = patientId;
        this.physicianNo = physicianNo;
        this.physicianId = physicianId;
        this.treatTypeCode = treatTypeCode;
        this.urgencyYn = urgencyYn;
        this.orderStatusCode = orderStatusCode;
        this.receivedAt = receivedAt;
    }

    @PrePersist
    private void generateId() {
        if (this.labOrderId == null) {
            this.labOrderId = UUID.randomUUID().toString();
        }
    }

    public void addOrderItem(LabOrderItemEntity item) {
        this.orderItems.add(item);
        item.assignLabOrder(this);
    }

    public void addReception(LabReceptionEntity reception) {
        this.receptions.add(reception);
        reception.assignLabOrder(this);
    }
}
