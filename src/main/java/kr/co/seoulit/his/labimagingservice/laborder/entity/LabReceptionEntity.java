package kr.co.seoulit.his.labimagingservice.laborder.entity;

import jakarta.persistence.*;
import kr.co.seoulit.his.labimagingservice.common.entity.BaseAuditEntity;
import kr.co.seoulit.his.labimagingservice.labschedule.entity.LabScheduleEntity;
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

    @OneToMany(mappedBy = "labReception", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<LabScheduleEntity> schedules = new ArrayList<>();

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
    public void addSchedule(LabScheduleEntity schedule) {
        this.schedules.add(schedule);
        schedule.assignLabReception(this);
    }
    void assignLabOrder(LabOrderEntity labOrder) {
        this.labOrder = labOrder;
    }
}
