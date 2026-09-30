package kr.co.seoulit.his.labimagingservice.labresult.microbiology.entity;

import jakarta.persistence.*;
import kr.co.seoulit.his.labimagingservice.common.entity.BaseAuditEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 항생제 감수성 결과 (MICROBIOLOGY_SUSCEPTIBILITY)
 * 대응 유스케이스: UC-RST-02 미생물검사결과등록 (Jira ZP2-14)
 *
 * ⚠ 한 결과 안에서 항생제가 겹치면 안 되는데 DB 에 UNIQUE 가 없다. 서비스가 막는다(LAB078).
 */
@Entity
@Table(name = "MICROBIOLOGY_SUSCEPTIBILITY")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MicrobiologySusceptibilityEntity extends BaseAuditEntity {

    @Id
    @Column(name = "microbiology_susceptibility_id", length = 36, nullable = false, updatable = false)
    private String microbiologySusceptibilityId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "microbiology_result_id", nullable = false)
    private MicrobiologyResultEntity microbiologyResult;

    /** 항생제코드 (공통코드 ANTIBIOTIC_CD) */
    @Column(name = "antibiotic_code", length = 20, nullable = false)
    private String antibioticCode;

    /** 감수성판정코드 (공통코드 SUSCEPTIBILITY_RESULT_CD: 01 S / 02 I / 03 R) */
    @Column(name = "susceptibility_result_code", length = 10, nullable = false)
    private String susceptibilityResultCode;

    @Builder
    public MicrobiologySusceptibilityEntity(String antibioticCode, String susceptibilityResultCode) {
        this.antibioticCode = antibioticCode;
        this.susceptibilityResultCode = susceptibilityResultCode;
    }

    @PrePersist
    private void generateId() {
        if (this.microbiologySusceptibilityId == null) {
            this.microbiologySusceptibilityId = UUID.randomUUID().toString();
        }
    }

    void assignMicrobiologyResult(MicrobiologyResultEntity microbiologyResult) {
        this.microbiologyResult = microbiologyResult;
    }
}
