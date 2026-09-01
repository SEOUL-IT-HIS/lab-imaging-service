package kr.co.seoulit.his.labimagingservice.labspecimen.entity;

import jakarta.persistence.*;
import kr.co.seoulit.his.labimagingservice.common.entity.BaseAuditEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 검체 인수/적합성 판정 (SPECIMEN_ACCEPTANCE)
 * 대응 유스케이스: UC-SPC-04 검체적합성판정 (Jira ZP2-8)
 * ⚠ fitness_status_code(검체상태코드)는 admin 공통코드가 아니라 서비스 내부 Enum으로 관리한다.
 *   (2026-08-04 공통코드 재분류 회의 결정 — 적합/부적합 이진값이라 운영 중 변할 여지가 없음)
 *   이 도메인에서만 쓰므로 common/status 가 아니라 labspecimen 아래에 두는 것이 맞다.
 */
@Entity
@Table(name = "SPECIMEN_ACCEPTANCE")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SpecimenAcceptanceEntity extends BaseAuditEntity {

    @Id
    @Column(name = "specimen_acceptance_id", length = 36, nullable = false, updatable = false)
    private String specimenAcceptanceId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "specimen_id", nullable = false, unique = true)
    private SpecimenEntity specimen;

    @Column(name = "accepted_at", nullable = false )
    private LocalDateTime acceptedAt;

    @Column(name = "accepted_by_id", length = 20, nullable = false )
    private String acceptedById;

    /**
     * ⚠ @Enumerated(EnumType.STRING) 을 빠뜨리면 안 된다.
     *   기본값 ORDINAL 은 "FIT" 대신 선언 순서 숫자(0,1)로 저장해, 나중에 상수 순서를 바꾸면
     *   이미 저장된 판정의 의미가 뒤집힌다. (SpecimenEntity.specimenTypeCode 와 같은 이유)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "fitness_status_code", length = 10, nullable = false )
    private FitnessStatus fitnessStatusCode;

    /**
     * ⚠ 길이가 30 이다. 다른 공통코드 컬럼(10)과 다른 것은 실수가 아니다.
     *   admin 의 SPECIMEN_REJECT_CD 가 영문 약어(INSUFFICIENT 등)였을 때 10자를 넘겨서 늘린 길이다.
     *   (2026-08-24 — 10자였을 때 부적합 판정이 LAB998 로 막혔다)
     *
     * ⚠ 지금은 그 그룹의 코드값이 숫자 2자리(01, 02 ...)로 바뀌어 30자가 필요하지는 않다.
     *   그래도 줄이지 않는다. 코드값 길이는 admin 이 정하는 것이라 언제든 다시 길어질 수 있고,
     *   컬럼을 줄이는 순간 같은 장애가 되풀이된다. 넉넉한 쪽으로 남겨 두는 편이 안전하다.
     */
    @Column(name = "unfit_reason_code", length = 30)
    private String unfitReasonCode;

    @Column(name = "recollection_requested_yn", columnDefinition = "CHAR(1)", nullable = false)
    private String recollectionRequestedYn;

    @Builder
    public SpecimenAcceptanceEntity(LocalDateTime acceptedAt, String acceptedById, FitnessStatus fitnessStatusCode,
                                    String unfitReasonCode, String recollectionRequestedYn) {
        this.acceptedAt = acceptedAt;
        this.acceptedById = acceptedById;
        this.fitnessStatusCode = fitnessStatusCode;
        this.unfitReasonCode = unfitReasonCode;
        this.recollectionRequestedYn = recollectionRequestedYn;
    }

    @PrePersist
    private void generateId() {
        if (this.specimenAcceptanceId == null) {
            this.specimenAcceptanceId = UUID.randomUUID().toString();
        }
    }

    public void assignSpecimen(SpecimenEntity specimenEntity) {
        this.specimen = specimenEntity;
    }
}
