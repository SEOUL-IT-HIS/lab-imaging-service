package kr.co.seoulit.his.labimagingservice.labschedule.entity;


import jakarta.persistence.*;
import kr.co.seoulit.his.labimagingservice.common.entity.BaseAuditEntity;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabReceptionEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "LAB_SCHEDULE")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LabScheduleEntity extends BaseAuditEntity{

    @Id
    @Column(name = "lab_schedule_id", length = 36, nullable = false, updatable = false)
    private String labScheduleId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lab_reception_id", nullable = false)
    private LabReceptionEntity labReception;

    @Column(name = "schedule_type_code", length = 10, nullable = false)
    private String scheduleTypeCode;

    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    @Column(name = "reservation_yn", columnDefinition = "CHAR(1)", nullable = false)
    private String reservationYn;

    @Column(name = "guidance_note", length = 500)
    private String guidanceNote;

    @Column(name = "confirmed_by_id", length = 20, nullable = false)
    private String confirmedById;

    @Column(name = "latest_yn", columnDefinition = "CHAR(1)", nullable = false)
    private String latestYn;

    @Builder
    public LabScheduleEntity (String scheduleTypeCode, LocalDateTime scheduledAt,
                              String reservationYn, String guidanceNote, String confirmedById,
                              String latestYn) {
        this.scheduleTypeCode = scheduleTypeCode;
        this.scheduledAt = scheduledAt;
        this.reservationYn = reservationYn;
        this.guidanceNote = guidanceNote;
        this.confirmedById = confirmedById;
        this.latestYn = latestYn;
    }

    @PrePersist
    private void generateId() {
        if (this.labScheduleId == null) {
            this.labScheduleId = UUID.randomUUID().toString();
        }
    }

    public void assignLabReception(LabReceptionEntity labReception) {
        this.labReception = labReception;
    }
}
