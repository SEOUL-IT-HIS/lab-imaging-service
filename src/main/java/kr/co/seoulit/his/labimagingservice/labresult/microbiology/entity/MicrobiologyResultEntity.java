package kr.co.seoulit.his.labimagingservice.labresult.microbiology.entity;

import jakarta.persistence.*;
import kr.co.seoulit.his.labimagingservice.common.entity.BaseAuditEntity;
import kr.co.seoulit.his.labimagingservice.labspecimen.entity.SpecimenEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 미생물검사결과 (MICROBIOLOGY_RESULT)
 * 대응 유스케이스: UC-RST-02 미생물검사결과등록 (Jira ZP2-14)
 *
 * ⚠ 검체(SPECIMEN) 1건당 1행이다(UQ_MBRS_SPEC). 검사항목(LAB_ORDER_ITEM)에 직접 붙지 않는다.
 *   진행도·청구·결과전송은 항목 단위라, "접수당 미생물 항목 정확히 1개 + 접수당 결과 1건" 제약으로
 *   결과 → 항목을 하나로 정한다. (5차 결정 — MicrobiologyResultService 주석 참고)
 *
 * ⚠ 중간보고/최종보고 이력 테이블은 없다(D4). result_status_code 01(등록)=중간보고, 02(확정)=최종보고.
 *   확정 전에는 수정할 수 있고, 수정 시 updated_at 만 바뀐다.
 *
 * ⚠ 컬럼은 실제 DB(2026-09-28 실측)와 맞췄다. ddl-auto=validate 라 한 글자라도 다르면 기동이 막힌다.
 *   recorded_by_id / confirmed_by_id 는 VARCHAR2(36) (lab_imaging_schema_최종보강.sql PART 1-1).
 */
@Entity
@Table(name = "MICROBIOLOGY_RESULT")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MicrobiologyResultEntity extends BaseAuditEntity {

    @Id
    @Column(name = "microbiology_result_id", length = 36, nullable = false, updatable = false)
    private String microbiologyResultId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "specimen_id", nullable = false, unique = true)
    private SpecimenEntity specimen;

    /** 배양상태코드 (공통코드 CULTURE_STATUS_CD: 01 배양중 / 02 음성 / 03 양성) */
    @Column(name = "culture_status_code", length = 10, nullable = false)
    private String cultureStatusCode;

    /** 균종코드 (공통코드 ORGANISM_CD). 동정 전·음성은 NULL */
    @Column(name = "organism_code", length = 20)
    private String organismCode;

    /** 원인균 여부 Y/N (CK_MBRS_CAU). 균종이 없으면 NULL */
    @Column(name = "causative_yn", columnDefinition = "CHAR(1)")
    private String causativeYn;

    @Lob
    @Column(name = "observation_note")
    private String observationNote;

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

    /**
     * 항생제 감수성 목록 (1:N).
     * ⚠ 수정은 "통째로 교체"다. 행을 골라 고치는 API 를 따로 두지 않았다 — 화면이 표 전체를 다시 보낸다.
     *   orphanRemoval 로 빠진 행은 DELETE 된다.
     */
    @OneToMany(mappedBy = "microbiologyResult", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt asc")
    private List<MicrobiologySusceptibilityEntity> susceptibilities = new ArrayList<>();

    @Builder
    public MicrobiologyResultEntity(String cultureStatusCode, String organismCode, String causativeYn,
                                    String observationNote, String resultStatusCode,
                                    LocalDateTime recordedAt, String recordedById) {
        this.cultureStatusCode = cultureStatusCode;
        this.organismCode = organismCode;
        this.causativeYn = causativeYn;
        this.observationNote = observationNote;
        this.resultStatusCode = resultStatusCode;
        this.recordedAt = recordedAt;
        this.recordedById = recordedById;
    }

    @PrePersist
    private void generateId() {
        if (this.microbiologyResultId == null) {
            this.microbiologyResultId = UUID.randomUUID().toString();
        }
    }

    public void assignSpecimen(SpecimenEntity specimen) {
        this.specimen = specimen;
    }

    public void modifyResult(String cultureStatusCode, String organismCode, String causativeYn,
                             String observationNote) {
        this.cultureStatusCode = cultureStatusCode;
        this.organismCode = organismCode;
        this.causativeYn = causativeYn;
        this.observationNote = observationNote;
    }

    /** 감수성 목록을 통째로 교체한다. (등록·수정 공용) */
    public void replaceSusceptibilities(List<MicrobiologySusceptibilityEntity> next) {
        this.susceptibilities.clear();
        for (MicrobiologySusceptibilityEntity s : next) {
            s.assignMicrobiologyResult(this);
            this.susceptibilities.add(s);
        }
    }

    public void confirm(String resultStatusCode, String confirmedById, LocalDateTime confirmedAt) {
        this.resultStatusCode = resultStatusCode;
        this.confirmedById = confirmedById;
        this.confirmedAt = confirmedAt;
    }
}
