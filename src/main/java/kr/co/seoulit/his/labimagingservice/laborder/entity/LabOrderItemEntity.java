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

import java.util.UUID;

/**
 * 검사오더상세 (LAB_ORDER_ITEM)
 */
@Entity
@Table(name = "LAB_ORDER_ITEM")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LabOrderItemEntity extends BaseAuditEntity {

    @Id
    @Column(name = "lab_order_item_id", length = 36, nullable = false, updatable = false)
    private String labOrderItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lab_order_id", nullable = false)
    private LabOrderEntity labOrder;

    @Column(name = "lab_item_code", length = 20, nullable = false)
    private String labItemCode;

    @Column(name = "item_status_code", length = 10)
    private String itemStatusCode;

    @Builder
    public LabOrderItemEntity(String labItemCode, String itemStatusCode) {
        this.labItemCode = labItemCode;
        this.itemStatusCode = itemStatusCode;
    }

    @PrePersist
    private void generateId() {
        if (this.labOrderItemId == null) {
            this.labOrderItemId = UUID.randomUUID().toString();
        }
    }

    void assignLabOrder(LabOrderEntity labOrder) {
        this.labOrder = labOrder;
    }
}
