package kr.co.seoulit.his.labimagingservice.imagingschedule.entity;

import jakarta.persistence.*;
import kr.co.seoulit.his.labimagingservice.common.entity.BaseAuditEntity;
import kr.co.seoulit.his.labimagingservice.imagingorder.entity.ImageOrderItemEntity;
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

    /**
     * 이 일정이 어느 촬영항목의 것인지. (2026-09-03 — 접수 단위 → 촬영항목 단위로 변경)
     *
     * ⚠ 접수 단위였을 때는 CT·MRI·초음파가 한 접수에 있어도 촬영실·장비·시각을 하나만 정할 수 있었다.
     *   셋은 서로 다른 방과 장비를 쓰고 같은 시각에 할 수도 없어서, 실제 업무를 담지 못했다.
     *
     * ⚠ image_reception_id 도 함께 남겨 둔다. 어느 방문 건의 일정인지 알아야 하고,
     *   워크리스트가 접수를 행 단위로 잡고 있어 접수로 되짚을 길이 필요하다.
     *   최종 일정 UNIQUE(UX_ISCH_LATEST)도 (접수, 항목) 조합으로 걸려 있다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "image_order_item_id", nullable = false)
    private ImageOrderItemEntity imageOrderItem;

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
    public void assignImageOrderItem(ImageOrderItemEntity imageOrderItem) {
        this.imageOrderItem = imageOrderItem;
    }

    public void assignImageReception(ImageReceptionEntity imageReception) {
        this.imageReception = imageReception;
    }
    public void markAsNotLatest() {//스케쥴 재등록 시 기존 스케쥴이 최종본이 아님을 나타냄
        this.latestYn = "N";
    }
}
