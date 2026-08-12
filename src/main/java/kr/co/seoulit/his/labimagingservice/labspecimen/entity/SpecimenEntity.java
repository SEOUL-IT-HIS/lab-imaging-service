package kr.co.seoulit.his.labimagingservice.labspecimen.entity;

import jakarta.persistence.*;
import kr.co.seoulit.his.labimagingservice.common.entity.BaseAuditEntity;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabReceptionEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 검체 (SPECIMEN)
 * 대응 유스케이스: UC-SPC-03 검체식별관리 (Jira ZP2-7)
 * ⚠ spring.jpa.hibernate.ddl-auto=validate 이므로 필드를 추가할 때마다
 *   컬럼명·타입이 DB와 정확히 일치해야 한다. 어긋나면 기동 자체가 실패한다.
 */
@Entity
@Table(name = "SPECIMEN")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SpecimenEntity extends BaseAuditEntity {

    @Id
    @Column(name = "specimen_id", length = 36, nullable = false, updatable = false)
    private String specimenId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lab_reception_id", nullable = false)
    private LabReceptionEntity labReception;

    @Column(name = "specimen_barcode", length = 30, nullable = false, unique = true)
    private String specimenBarcode;

    @Column(name = "specimen_container_code", length = 10, nullable = false )
    private String specimenContainerCode;

    /**
     * ⚠ @Enumerated(EnumType.STRING) 은 빠뜨리면 안 된다.
     *   JPA 기본값은 ORDINAL 이라 "BLOOD" 대신 선언 순서 숫자(0,1,2...)로 저장된다.
     *   그러면 나중에 enum 상수 순서를 바꾸거나 중간에 하나 끼워 넣는 순간
     *   이미 저장된 행의 의미가 통째로 어긋난다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "specimen_type_code", length = 10, nullable = false )
    private SpecimenType specimenTypeCode;

    @Column(name = "patient_no", length = 20, nullable = false )
    private String patientNo;

    @Column(name = "patient_id", length = 36 )
    private String patientId;

    @Column(name = "collected_at", nullable = false )
    private LocalDateTime collectedAt;

    @Column(name = "collected_by_id", length = 20, nullable = false )
    private String collectedById;

    @OneToOne(mappedBy = "specimen", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private SpecimenAcceptanceEntity specimenAcceptance;

    @Builder
    public SpecimenEntity(String specimenBarcode, String specimenContainerCode, SpecimenType specimenTypeCode,
                          String patientNo, String patientId, LocalDateTime collectedAt, String collectedById) {

        this.specimenBarcode = specimenBarcode;
        this.specimenContainerCode = specimenContainerCode;
        this.specimenTypeCode = specimenTypeCode;
        this.patientNo = patientNo;
        this.patientId = patientId;
        this.collectedAt = collectedAt;
        this.collectedById = collectedById;
    }

    @PrePersist
    private void generateId() {
        if (this.specimenId == null) {
            this.specimenId = UUID.randomUUID().toString();
        }
    }
    public void assignSpecimenAcceptance(SpecimenAcceptanceEntity specimenAcceptance) {
        this.specimenAcceptance = specimenAcceptance;
        specimenAcceptance.assignSpecimen(this);
    }

    public void assignLabReception(LabReceptionEntity labReception) {
        this.labReception = labReception;
    }
}
