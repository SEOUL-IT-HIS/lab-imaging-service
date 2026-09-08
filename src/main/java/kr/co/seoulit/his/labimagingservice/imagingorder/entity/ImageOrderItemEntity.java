package kr.co.seoulit.his.labimagingservice.imagingorder.entity;

import kr.co.seoulit.his.labimagingservice.common.entity.BaseAuditEntity;
import kr.co.seoulit.his.labimagingservice.common.status.OrderItemStatus;
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

    /**
     * 촬영 완료로 상태를 전이한다. (ZP2-106)
     *
     * ⚠ 상태 변경을 setter 가 아니라 의미 있는 메서드로 열어 둔다. (ImageReceptionEntity.exclude
     *   와 같은 원칙) 지금은 값 하나만 바꾸지만, 나중에 "언제 촬영완료로 바뀌었는지"를 같이
     *   남겨야 한다면 이 메서드 하나만 고치면 된다.
     *
     * ⚠ 이미 ACQUIRED 인 항목에 다시 호출해도 안전하다(멱등). 재촬영으로 파일이 추가될 때마다
     *   호출되므로, 상태가 이미 같은 값이어도 예외를 던지지 않는다 — 예외를 던지면 두 번째
     *   파일부터 업로드가 막힌다.
     */
    public void markAcquired() {
        this.itemStatusCode = OrderItemStatus.ACQUIRED.name();
    }
}
