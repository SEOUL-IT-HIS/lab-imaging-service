package kr.co.seoulit.his.labimagingservice.imagingorder.entity;

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
    public ImageReceptionEntity(String receptionNo, String patientNo, String receptionStatusCode,
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
        if (this.imageReceptionId == null) {
            this.imageReceptionId = UUID.randomUUID().toString();
        }
    }

    void assignImageOrder(ImageOrderEntity imageOrder) {
        this.imageOrder = imageOrder;
    }
}
