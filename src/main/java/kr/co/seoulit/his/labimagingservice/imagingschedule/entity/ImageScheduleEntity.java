package kr.co.seoulit.his.labimagingservice.imagingschedule.entity;

import jakarta.persistence.*;
import kr.co.seoulit.his.labimagingservice.common.entity.BaseAuditEntity;
import kr.co.seoulit.his.labimagingservice.imagingorder.entity.ImageReceptionEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "IMAGE_SCHEDULE")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ImageScheduleEntity extends BaseAuditEntity {

    @Id
    @Column(name = "image_schedule_id", length = 36, nullable = false, updatable = false)
    private String imageScheduleId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "image_reception_id", nullable = false)
    private ImageReceptionEntity imageReception;

    @Column(name = "room_code", length = 10, nullable = false)
    private String roomCode;

    @Column(name = "equipment_code", length = 10, nullable = false)
    private String equipmentCode;

    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    @Column(name = "reservation_yn", columnDefinition = "CHAR(1)", nullable = false)
    private String reservationYn;

    @Column(name = "contraindication_check_code", length = 10, nullable = false)
    private String contraindicationCheckCode;

    @Column(name = "contraindication_note", length = 500)
    private String contraindicationNote;

    @Column(name = "confirmed_by_id", length = 20, nullable = false)
    private String confirmedById;

    @Column(name = "latest_yn", columnDefinition = "CHAR(1)", nullable = false)
    private String latestYn;

    @Builder
    public ImageScheduleEntity(String roomCode, String equipmentCode, LocalDateTime scheduledAt,
                               String reservationYn, String contraindicationCheckCode, String contraindicationNote, String confirmedById,
                               String latestYn) {
        this.roomCode = roomCode;
        this.equipmentCode = equipmentCode;
        this.scheduledAt = scheduledAt;
        this.reservationYn = reservationYn;
        this.contraindicationCheckCode = contraindicationCheckCode;
        this.contraindicationNote = contraindicationNote;
        this.confirmedById = confirmedById;
        this.latestYn = latestYn;
    }

    @PrePersist
    private void generateId() {
        if (this.imageScheduleId == null) {
            this.imageScheduleId = UUID.randomUUID().toString();
        }
    }
    public void assignImageReception(ImageReceptionEntity imageReception) {
        this.imageReception = imageReception;
    }
    public void markAsNotLatest() {//스케쥴 재등록 시 기존 스케쥴이 최종본이 아님을 나타냄
        this.latestYn = "N";
    }
}
