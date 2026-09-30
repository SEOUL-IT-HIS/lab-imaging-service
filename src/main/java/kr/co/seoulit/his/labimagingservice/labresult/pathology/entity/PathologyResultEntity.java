package kr.co.seoulit.his.labimagingservice.labresult.pathology.entity;

import jakarta.persistence.*;
import kr.co.seoulit.his.labimagingservice.common.entity.BaseAuditEntity;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabOrderItemEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 병리검사결과 (PATHOLOGY_RESULT)
 * 대응 유스케이스: UC-RST-03 병리검사결과등록 (Jira ZP2-15)
 *
 * ⚠ 검사항목(LAB_ORDER_ITEM) 1건당 1행(UQ_PATH_LORI). 일반검사 결과와 같은 단위라 진행도·청구·전송이 그대로 맞는다.
 *
 * ⚠ 소견 컬럼이 findings 하나뿐이다(D6). 명세의 육안/현미경/진단 소견은 화면에서 구획을 나눠 받아
 *   구획 제목을 붙여 한 CLOB 에 합쳐 저장한다. DDL 은 바꾸지 않는다.
 *
 * ⚠ 첨부는 SeaweedFS 에 두고 키만 저장한다(attachment_file_key). 콘텐츠타입·파일명 컬럼이 없어
 *   다운로드 때는 키(…/{uuid}_{원본파일명})에서 파일명과 확장자를 되살린다(PathologyResultService).
 *
 * ⚠ 컬럼은 실제 DB(2026-09-28 실측)와 맞췄다. recorded_by_id / confirmed_by_id 는 VARCHAR2(36).
 */
@Entity
@Table(name = "PATHOLOGY_RESULT")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PathologyResultEntity extends BaseAuditEntity {

    @Id
    @Column(name = "pathology_result_id", length = 36, nullable = false, updatable = false)
    private String pathologyResultId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lab_order_item_id", nullable = false, unique = true)
    private LabOrderItemEntity labOrderItem;

    /** 병리유형코드 (PATHOLOGY_TYPE_CD: 01 조직 / 02 세포) */
    @Column(name = "pathology_type_code", length = 10, nullable = false)
    private String pathologyTypeCode;

    /** 병리진단명코드 (PATHOLOGY_DIAGNOSIS_CD) — 선택 */
    @Column(name = "diagnosis_code", length = 20)
    private String diagnosisCode;

    @Lob
    @Column(name = "findings", nullable = false)
    private String findings;

    @Column(name = "attachment_file_key", length = 200)
    private String attachmentFileKey;

    @Column(name = "result_status_code", length = 10, nullable = false)
    private String resultStatusCode;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    @Column(name = "recorded_by_id", length = 36, nullable = false)
    private String recordedById;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "confirmed_by_id", length = 36)
    private String confirmedById;

    @Builder
    public PathologyResultEntity(String pathologyTypeCode, String diagnosisCode, String findings,
                                 String attachmentFileKey, String resultStatusCode,
                                 LocalDateTime recordedAt, String recordedById) {
        this.pathologyTypeCode = pathologyTypeCode;
        this.diagnosisCode = diagnosisCode;
        this.findings = findings;
        this.attachmentFileKey = attachmentFileKey;
        this.resultStatusCode = resultStatusCode;
        this.recordedAt = recordedAt;
        this.recordedById = recordedById;
    }

    @PrePersist
    private void generateId() {
        if (this.pathologyResultId == null) {
            this.pathologyResultId = UUID.randomUUID().toString();
        }
    }

    public void assignLabOrderItem(LabOrderItemEntity labOrderItem) {
        this.labOrderItem = labOrderItem;
    }

    public void modifyResult(String pathologyTypeCode, String diagnosisCode, String findings) {
        this.pathologyTypeCode = pathologyTypeCode;
        this.diagnosisCode = diagnosisCode;
        this.findings = findings;
    }

    /** 첨부 교체. 이전 키를 돌려준다 — 호출한 쪽이 커밋 후 이전 파일을 정리할 수 있게. */
    public String replaceAttachment(String attachmentFileKey) {
        String previous = this.attachmentFileKey;
        this.attachmentFileKey = attachmentFileKey;
        return previous;
    }

    public void confirm(String resultStatusCode, String confirmedById, LocalDateTime confirmedAt) {
        this.resultStatusCode = resultStatusCode;
        this.confirmedById = confirmedById;
        this.confirmedAt = confirmedAt;
    }
}
