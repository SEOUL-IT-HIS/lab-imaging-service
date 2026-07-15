package kr.co.seoulit.his.labimagingservice.imagingorder.entity;

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
 * 영상오더 (IMAGE_ORDER)
 * 대응 유스케이스: UC-IMG-01 영상오더접수
 */
@Entity
@Table(name = "IMAGE_ORDER")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ImageOrderEntity extends BaseAuditEntity {

    @Id
    @Column(name = "image_order_id", length = 36, nullable = false, updatable = false)
    private String imageOrderId;

    @Column(name = "image_order_no", length = 20, nullable = false, unique = true)
    private String imageOrderNo;

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

    @OneToMany(mappedBy = "imageOrder", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ImageOrderItemEntity> orderItems = new ArrayList<>();

    @OneToMany(mappedBy = "imageOrder", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ImageReceptionEntity> receptions = new ArrayList<>();

    @Builder
    public ImageOrderEntity(String imageOrderNo, String systemCode, String patientNo, String physicianNo,
                             String treatTypeCode, String urgencyYn, String orderStatusCode, LocalDateTime receivedAt) {
        this.imageOrderNo = imageOrderNo;
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
        if (this.imageOrderId == null) {
            this.imageOrderId = UUID.randomUUID().toString();
        }
    }

    public void addOrderItem(ImageOrderItemEntity item) {
        this.orderItems.add(item);
        item.assignImageOrder(this);
    }

    public void addReception(ImageReceptionEntity reception) {
        this.receptions.add(reception);
        reception.assignImageOrder(this);
    }
}
