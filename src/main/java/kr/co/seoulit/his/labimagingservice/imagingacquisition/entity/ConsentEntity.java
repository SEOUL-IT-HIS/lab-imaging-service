package kr.co.seoulit.his.labimagingservice.imagingacquisition.entity;

import jakarta.persistence.*;
import kr.co.seoulit.his.labimagingservice.common.entity.BaseAuditEntity;
import kr.co.seoulit.his.labimagingservice.imagingorder.entity.ImageOrderEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 동의서 (CONSENT)
 * 대응 유스케이스: UC-IMG-05 조영제/침습검사 동의 등록 (Jira ZP2-28)
 *
 * ⚠ 전자문서(파일)는 저장하지 않는다. 빈 양식은 admin-service 문서양식관리가 소유하고,
 *   이 서비스는 동의 여부·서명자·확인일시 등 업무 데이터만 보유한다. (2026-07-11 문서관리 회의)
 * ⚠ document_template_id 는 admin-service DOCUMENT_TEMPLATE 참조지만 DB FK가 아니라
 *   서비스 간 논리적 참조다. 그래서 @ManyToOne 이 아니라 단순 컬럼으로 둔다.
 * ⚠ signed_by_name 은 타 서비스 소유 데이터의 스냅샷이 아니라 이 화면에서 직접 입력·확정된
 *   원본 값이므로 저장하는 것이 맞다. (개발표준가이드 14.1 스냅샷 금지의 예외)
 * ⚠ consent_dt 만 DDL 타입이 DATE 다(나머지 일시 컬럼은 TIMESTAMP). 논리명도 "동의일자"라
 *   LocalDateTime 이 아니라 LocalDate 로 매핑했다.
 */
@Entity
@Table(name = "CONSENT")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConsentEntity extends BaseAuditEntity {

    @Id
    @Column(name = "consent_id", length = 36, nullable = false, updatable = false)
    private String consentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "image_order_id", nullable = false)
    private ImageOrderEntity imageOrder;

    @Column(name = "patient_no", length = 20, nullable = false)
    private String patientNo;

    @Column(name = "patient_id", length = 36)
    private String patientId;

    @Column(name = "consent_type_code", length = 10, nullable = false)
    private String consentTypeCode;

    @Column(name = "document_template_id", length = 36, nullable = false)
    private String documentTemplateId;

    @Column(name = "consent_yn", columnDefinition = "CHAR(1)", nullable = false)
    private String consentYn;

    @Column(name = "consent_dt", nullable = false)
    private LocalDate consentDt;

    @Column(name = "signed_by_name", length = 50, nullable = false)
    private String signedByName;

    @Column(name = "witness_id", length = 20, nullable = false)
    private String witnessId;

    @Column(name = "withdrawn_yn", columnDefinition = "CHAR(1)", nullable = false)
    private String withdrawnYn;

    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;

    /**
     * ⚠ 길이가 30 이다. admin 의 CONSENT_WITHDRAW_CD 가 영문 약어(CONDITION_CHANGE 등)였을 때
     *   10자를 넘겨서 늘린 길이다. 철회 기능을 붙이는 순간 10자였으면 바로 실패했을 것이다.
     *   (2026-08-24 — 같은 원인으로 검체 부적합사유가 막힌 뒤 함께 확인)
     *
     * ⚠ 지금은 그 그룹의 코드값이 숫자 2자리(01, 02 ...)로 바뀌어 30자가 필요하지는 않다.
     *   그래도 줄이지 않는다. 코드값 길이는 admin 이 정하는 것이라 언제든 다시 길어질 수 있다.
     *   (SpecimenAcceptanceEntity.unfitReasonCode 와 같은 판단)
     */
    @Column(name = "withdrawn_reason_code", length = 30)
    private String withdrawnReasonCode;

    @Builder
    public ConsentEntity(String patientNo, String patientId, String consentTypeCode,
                         String documentTemplateId, String consentYn, LocalDate consentDt,
                         String signedByName, String witnessId, String withdrawnYn) {
        this.patientNo = patientNo;
        this.patientId = patientId;
        this.consentTypeCode = consentTypeCode;
        this.documentTemplateId = documentTemplateId;
        this.consentYn = consentYn;
        this.consentDt = consentDt;
        this.signedByName = signedByName;
        this.witnessId = witnessId;
        this.withdrawnYn = withdrawnYn;
    }

    @PrePersist
    private void generateId() {
        if (this.consentId == null) {
            this.consentId = UUID.randomUUID().toString();
        }
    }

    public void assignImageOrder(ImageOrderEntity imageOrder) {
        this.imageOrder = imageOrder;
    }

    /**
     * 동의 철회 처리. (withdrawn_yn = 'Y' 로 전환하고 철회 시각·사유를 기록)
     *
     * ⚠ 기존 행을 UPDATE 하는 방식이다. 일정(LAB_SCHEDULE)의 latest_yn 처럼 신규 행을
     *   INSERT 해 이력을 남기는 방식과 다르다. withdrawn_* 컬럼이 같은 행에 있는 DDL 구조를
     *   따른 것이고, 이력 보존이 필요하다고 결론나면 이 메서드부터 바뀌어야 한다.
     */
    public void withdraw(String withdrawnReasonCode, LocalDateTime withdrawnAt) {
        this.withdrawnYn = "Y";
        this.withdrawnReasonCode = withdrawnReasonCode;
        this.withdrawnAt = withdrawnAt;
    }
}
