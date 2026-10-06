package kr.co.seoulit.his.labimagingservice.laborder.entity;

import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.entity.BaseAuditEntity;
import kr.co.seoulit.his.labimagingservice.common.exception.LabImagingBusinessException;
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

    /** 항목을 취소 상태로 전환한다. (05번 지시서 Phase 2-B) */
    public void cancel() {
        this.itemStatusCode = OrderItemStatus.CANCELLED.name();
    }

    /**
     * 취소된 항목이면 거절한다(LAB121). (05번 지시서 Phase 3)
     * LabReceptionEntity.requireNotCancelled() 와 같은 이유로 엔티티에 둔다 — 결과 등록/확정
     * 여러 지점에서 "이 항목이 취소됐는가"를 매번 같은 기준으로 묻는다.
     */
    public void requireNotCancelled() {
        if (OrderItemStatus.CANCELLED.name().equals(this.itemStatusCode)) {
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB121, "취소된 검사항목입니다. (labItemCode=" + this.labItemCode + ")");
        }
    }
}
