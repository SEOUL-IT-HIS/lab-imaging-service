package kr.co.seoulit.his.labimagingservice.imagingorder.entity;

import jakarta.persistence.*;
import kr.co.seoulit.his.labimagingservice.common.entity.BaseAuditEntity;
import kr.co.seoulit.his.labimagingservice.common.status.ReceptionStatus;
import kr.co.seoulit.his.labimagingservice.imagingschedule.entity.ImageScheduleEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 영상검사접수 (IMAGE_RECEPTION)
 * IMAGE_ORDER : IMAGE_RECEPTION = 1:N (2026-07-13 결정)
 */
@Entity
@Table(name = "IMAGE_RECEPTION")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ImageReceptionEntity extends BaseAuditEntity {

    @Id
    @Column(name = "image_reception_id", length = 36, nullable = false, updatable = false)
    private String imageReceptionId;

    @Column(name = "reception_no", length = 20, nullable = false, unique = true)
    private String receptionNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "image_order_id", nullable = false)
    private ImageOrderEntity imageOrder;

    /** 화면 표시용 업무번호. 검증·참조에는 쓰지 않는다. */
    @Column(name = "patient_no", length = 20, nullable = false)
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
     * 워크리스트에서 뺀 사유. 상태가 EXCLUDED 일 때만 값이 있다.
     *
     * ⚠ 사유를 필수로 받는다. 목록에서 사라진 건을 나중에 설명할 수 있는 근거가 이 기록뿐이다.
     * ⚠ 공통코드가 아니라 자유 텍스트다. 어떤 사유가 실제로 쓰이는지는 운영해봐야 알 수 있다.
     *   (검사 LAB_RECEPTION.exclusion_reason 과 같은 결정)
     */
    @Column(name = "exclusion_reason", length = 200)
    private String exclusionReason;

    /** 제외 처리 일시. 상태가 EXCLUDED 일 때만 값이 있다. */
    @Column(name = "excluded_at")
    private LocalDateTime excludedAt;

    @OneToMany(mappedBy = "imageReception", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ImageScheduleEntity> schedules = new ArrayList<>();

    @Builder
    public ImageReceptionEntity(String receptionNo, String patientNo, String patientId, String receptionStatusCode,
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
        if (this.imageReceptionId == null) {
            this.imageReceptionId = UUID.randomUUID().toString();
        }
    }
    /**
     * 워크리스트에서 제외한다. (담당자가 처리하지 않기로 판단한 건)
     *
     * ⚠ 상태 변경을 setter 가 아니라 의미 있는 메서드로 열어 둔다.
     *   상태만 바꾸는 코드가 생기면 "왜 뺐는지"가 비어 있는 행이 남는다. 세 값을 항상 함께 바꾼다.
     *   (LabReceptionEntity.exclude 와 같은 구조)
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

    public void addSchedule(ImageScheduleEntity schedule) {
        this.schedules.add(schedule);
        schedule.assignImageReception(this);
    }

    void assignImageOrder(ImageOrderEntity imageOrder) {
        this.imageOrder = imageOrder;
    }
}
