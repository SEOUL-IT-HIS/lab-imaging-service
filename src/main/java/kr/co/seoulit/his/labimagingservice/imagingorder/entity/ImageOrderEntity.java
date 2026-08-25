package kr.co.seoulit.his.labimagingservice.imagingorder.entity;

import kr.co.seoulit.his.labimagingservice.common.entity.BaseAuditEntity;
import kr.co.seoulit.his.labimagingservice.imagingacquisition.entity.ConsentEntity;
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

    // MSA 간 참조 식별자를 VARCHAR2(36)으로 통일 (2026-08-25). 처방코어의 prescriptionId 가 최대 36자.
    @Column(name = "image_order_no", length = 36, nullable = false, unique = true)
    private String imageOrderNo;

    @Column(name = "system_code", length = 10, nullable = false)
    private String systemCode;

    /** 화면 표시용 업무번호. 검증·참조에는 쓰지 않는다. */
    @Column(name = "patient_no", length = 20, nullable = false)
    private String patientNo;

    /** patient-service 내부 식별자. 참조/검증(API 호출)은 이 값을 쓴다. */
    @Column(name = "patient_id", length = 36)
    private String patientId;

    /** 화면 표시용 업무번호. */
    @Column(name = "physician_no", length = 20)
    private String physicianNo;

    /** 처방의 내부 식별자. 참조용. */
    @Column(name = "physician_id", length = 36)
    private String physicianId;

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

    /**
     * 조영제/침습검사 동의서 이력. (UC-IMG-05, 오더 1건에 재동의 이력 여러 건 가능)
     *
     * ⚠ 위 두 컬렉션과 달리 cascade / orphanRemoval 을 걸지 않았다.
     *   orderItems·receptions 는 오더 접수와 같은 트랜잭션에서 함께 생성되지만,
     *   동의서는 접수 이후 별도 시점에 ConsentService 가 등록한다. 저장은
     *   ConsentRepository 가 담당하므로 이 컬렉션은 조회(탐색) 용도다.
     *   특히 orphanRemoval 은 컬렉션에서 빼는 것만으로 동의 기록이 삭제되는데,
     *   동의서는 의무기록 성격이라 그런 경로를 열어두면 안 된다.
     *
     * ⚠ 저장 시 연관관계는 ConsentEntity#assignImageOrder 로 주인(owning) 쪽에서 맺는다.
     *   이 컬렉션은 같은 트랜잭션 안에서 즉시 갱신되지 않으니 재조회해야 반영된다.
     */
    @OneToMany(mappedBy = "imageOrder", fetch = FetchType.LAZY)
    private List<ConsentEntity> consents = new ArrayList<>();

    @Builder
    public ImageOrderEntity(String imageOrderNo, String systemCode, String patientNo, String patientId,
                             String physicianNo, String physicianId,
                             String treatTypeCode, String urgencyYn, String orderStatusCode, LocalDateTime receivedAt) {
        this.imageOrderNo = imageOrderNo;
        this.systemCode = systemCode;
        this.patientNo = patientNo;
        this.patientId = patientId;
        this.physicianNo = physicianNo;
        this.physicianId = physicianId;
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
