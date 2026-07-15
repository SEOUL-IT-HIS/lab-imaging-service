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

import java.util.UUID;

/**
 * 영상오더상세 (IMAGE_ORDER_ITEM)
 */
@Entity
@Table(name = "IMAGE_ORDER_ITEM")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ImageOrderItemEntity extends BaseAuditEntity {

    @Id
    @Column(name = "image_order_item_id", length = 36, nullable = false, updatable = false)
    private String imageOrderItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "image_order_id", nullable = false)
    private ImageOrderEntity imageOrder;

    @Column(name = "image_item_code", length = 20, nullable = false)
    private String imageItemCode;

    @Column(name = "item_status_code", length = 10)
    private String itemStatusCode;

    @Builder
    public ImageOrderItemEntity(String imageItemCode, String itemStatusCode) {
        this.imageItemCode = imageItemCode;
        this.itemStatusCode = itemStatusCode;
    }

    @PrePersist
    private void generateId() {
        if (this.imageOrderItemId == null) {
            this.imageOrderItemId = UUID.randomUUID().toString();
        }
    }

    void assignImageOrder(ImageOrderEntity imageOrder) {
        this.imageOrder = imageOrder;
    }
}
