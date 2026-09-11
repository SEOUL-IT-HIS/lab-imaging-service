package kr.co.seoulit.his.labimagingservice.imaginginterpretation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import kr.co.seoulit.his.labimagingservice.common.entity.BaseAuditEntity;
import kr.co.seoulit.his.labimagingservice.imagingorder.entity.ImageOrderItemEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 영상판독 (IMAGE_READING)
 * 대응 유스케이스: UC-IMG-04 영상판독처리 (Jira ZP2-23)
 *
 * ⚠ 촬영항목(IMAGE_ORDER_ITEM) 1건에 판독 1건이다(1:1). image_order_item_id 에 UNIQUE 가 걸려 있다.
 *   연관관계는 @ManyToOne 으로 선언하지만(방향은 1:N 형태), 실제로는 1:1 로 취급한다.
 *   (LabResultEntity 가 LAB_ORDER_ITEM 을 @OneToOne 으로 잡은 것과 같은 관계이지만, 여기서는
 *   findOrCreate 시점에 이미 로딩된 ImageOrderItemEntity 를 그대로 붙이는 용도라 @ManyToOne 으로도
 *   충분하다 — UNIQUE 제약은 DB 가 보장한다)
 *
 * ⚠ 이 행은 촬영(ZP2-21) 시점에 만들어지지 않는다. 판독 워크리스트/상세 조회가 처음 호출될 때
 *   findOrCreate 로 그 자리에서 생성된다. (ImageReadingService 클래스 주석 참고)
 *
 * ⚠ 상태는 대기(01) → 판독중(02) → 확정(03) 으로만 전이하고 되돌아가지 않는다.
 *   확정 시점을 signed_at / signed_by_id 로 남긴다. (LabResultEntity 의 확정 처리와 같은 원칙)
 */
@Entity
@Table(name = "IMAGE_READING")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ImageReadingEntity extends BaseAuditEntity {

    @Id
    @Column(name = "image_reading_id", length = 36, nullable = false, updatable = false)
    private String imageReadingId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "image_order_item_id", nullable = false, unique = true)
    private ImageOrderItemEntity imageOrderItem;

    /** 공통코드 READING_STATUS_CD — 01=대기, 02=판독중, 03=완료. 요청값이 아니라 서비스가 전이시킨다. */
    @Column(name = "reading_status_code", length = 10, nullable = false)
    private String readingStatusCode;

    /** 참조 식별자다. 직원 서비스에 존재 여부를 묻지 않는다. (LabResultEntity.recordedById 와 같은 취급) */
    @Column(name = "assigned_to_id", length = 20)
    private String assignedToId;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    /**
     * 판독 소견. 확정 전(01/02)에는 자유롭게 수정할 수 있다.
     * ⚠ CLOB 이라 @Lob 을 붙인다. 결과값(LabResultEntity.resultValue)과 달리 서술형 텍스트라
     *   길이 제한이 있는 VARCHAR2 로는 부족하다.
     */
    @Lob
    @Column(name = "findings")
    private String findings;

    /** 확정 전에는 비어 있다. 확정과 동시에 signed_at 과 함께 채워진다. */
    @Column(name = "signed_by_id", length = 20)
    private String signedById;

    @Column(name = "signed_at")
    private LocalDateTime signedAt;

    @Builder
    public ImageReadingEntity(String readingStatusCode) {
        this.readingStatusCode = readingStatusCode;
    }

    @PrePersist
    private void generateId() {
        if (this.imageReadingId == null) {
            this.imageReadingId = UUID.randomUUID().toString();
        }
    }

    public void assignImageOrderItem(ImageOrderItemEntity imageOrderItem) {
        this.imageOrderItem = imageOrderItem;
    }

    /**
     * 담당자를 배정한다. 대기(01) → 판독중(02) 전이.
     *
     * ⚠ 이미 판독중(02)인 건에 다시 호출해도 된다(담당자 변경). 상태값을 항상 02 로 다시
     *   써도 이미 02 라면 아무 것도 바뀌지 않는다 — "상태를 되돌리지 않는다"는 요구사항은
     *   호출하는 쪽(ImageReadingService)이 03(확정) 인 건을 걸러내는 것으로 지킨다.
     */
    public void assign(String assignedToId, LocalDateTime assignedAt, String readingStatusCode) {
        this.assignedToId = assignedToId;
        this.assignedAt = assignedAt;
        this.readingStatusCode = readingStatusCode;
    }

    /**
     * 소견을 고쳐 쓴다. (확정 전에만 호출된다 — 호출 전에 서비스가 상태를 확인한다)
     * ⚠ 상태·확정정보는 건드리지 않는다. (LabResultEntity.modifyResult 와 같은 원칙)
     */
    public void updateFindings(String findings) {
        this.findings = findings;
    }

    /**
     * 판독중/대기(01/02) → 확정(03) 으로 전이한다.
     * ⚠ 상태 전이와 확정정보 기록을 한 메서드에 둔다. (LabResultEntity.confirm 과 같은 이유)
     */
    public void confirm(String readingStatusCode, String signedById, LocalDateTime signedAt) {
        this.readingStatusCode = readingStatusCode;
        this.signedById = signedById;
        this.signedAt = signedAt;
    }
}
