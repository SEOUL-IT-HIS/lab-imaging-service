package kr.co.seoulit.his.labimagingservice.laborder.entity;

import kr.co.seoulit.his.labimagingservice.common.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
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

    @Column(name = "patient_no", length = 20, nullable = false)
    private String patientNo;

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

    @Builder
    public LabReceptionEntity(String receptionNo, String patientNo, String receptionStatusCode,
                               String urgencyYn, String receivedById, String ackSentYn, LocalDateTime ackSentAt) {
        this.receptionNo = receptionNo;
        this.patientNo = patientNo;
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

    void assignLabOrder(LabOrderEntity labOrder) {
        this.labOrder = labOrder;
    }
}
