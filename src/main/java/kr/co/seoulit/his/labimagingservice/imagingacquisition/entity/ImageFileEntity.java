package kr.co.seoulit.his.labimagingservice.imagingacquisition.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Id;
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
 * 영상파일 (IMAGE_FILE)
 * 대응 유스케이스: UC-IMG-03 영상판독대기등록 (Jira ZP2-21)
 *
 * ⚠ 촬영항목(IMAGE_ORDER_ITEM) : 영상파일 = 1:N 이다.
 *   한 항목을 여러 번 촬영하면(재촬영 등) 파일이 여러 건 쌓인다. (ImageScheduleEntity 와
 *   같은 참조 관계 — image_order_item_id 를 FK 로 갖는다)
 *
 * ⚠ 별도 이력 테이블을 두지 않는다. 이 1:N 구조 자체가 "촬영할 때마다 파일이 쌓이는" 이력
 *   역할을 한다 — latest_yn 같은 최종본 플래그도 없다. 재촬영 시 기존 행을 지우지 않고
 *   새 행만 추가한다(ImageFileService 참고). 판독 화면이 "이 항목의 마지막 파일"을 원하면
 *   uploaded_at 최댓값으로 고르면 되고, 이력을 보여줘야 하면 전체 목록을 그대로 쓰면 된다.
 *
 * ⚠ storage_key 는 SeaweedFS Filer 의 파일 경로 전체다("/image-files/{itemId}/{uuid}_{원본파일명}").
 *   fid(볼륨 내부 식별자)가 아니라 경로를 쓰는 이유는 이 프로젝트가 Filer HTTP API 를 쓰기
 *   때문이다 — Filer 는 경로 기반이고, fid 는 Volume 서버에 직접 붙을 때 쓰는 저수준 식별자다.
 *   (ImageFileService 의 SeaweedFS 연동 주석 참고)
 */
@Entity
@Table(name = "IMAGE_FILE")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ImageFileEntity extends BaseAuditEntity {

    @Id
    @Column(name = "image_file_id", length = 36, nullable = false, updatable = false)
    private String imageFileId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "image_order_item_id", nullable = false)
    private ImageOrderItemEntity imageOrderItem;

    @Column(name = "storage_key", length = 200, nullable = false)
    private String storageKey;

    @Column(name = "file_name", length = 200, nullable = false)
    private String fileName;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "content_type", length = 50, nullable = false)
    private String contentType;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    /** 참조 식별자다. 직원 서비스에 존재 여부를 묻지 않는다. (LabResultEntity.recordedById 와 같은 취급) */
    @Column(name = "uploaded_by_id", length = 20, nullable = false)
    private String uploadedById;

    @Builder
    public ImageFileEntity(String storageKey, String fileName, Long fileSize,
                           String contentType, LocalDateTime uploadedAt, String uploadedById) {
        this.storageKey = storageKey;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.contentType = contentType;
        this.uploadedAt = uploadedAt;
        this.uploadedById = uploadedById;
    }

    @PrePersist
    private void generateId() {
        if (this.imageFileId == null) {
            this.imageFileId = UUID.randomUUID().toString();
        }
    }

    public void assignImageOrderItem(ImageOrderItemEntity imageOrderItem) {
        this.imageOrderItem = imageOrderItem;
    }
}
