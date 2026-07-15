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

    @Column(name = "lab_order_no", length = 20, nullable = false, unique = true)
    private String labOrderNo;

    @Column(name = "system_code", length = 10, nullable = false)
    private String systemCode;

    @Column(name = "patient_no", length = 20, nullable = false)
    private String patientNo;

    @Column(name = "physician_no", length = 20)
    private String physicianNo;

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
    public LabOrderEntity(String labOrderNo, String systemCode, String patientNo, String physicianNo,
                           String treatTypeCode, String urgencyYn, String orderStatusCode, LocalDateTime receivedAt) {
        this.labOrderNo = labOrderNo;
        this.systemCode = systemCode;
        this.patientNo = patientNo;
        this.physicianNo = physicianNo;
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
