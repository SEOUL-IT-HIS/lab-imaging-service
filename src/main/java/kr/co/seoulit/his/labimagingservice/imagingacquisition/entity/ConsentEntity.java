package kr.co.seoulit.his.labimagingservice.imagingacquisition.entity;

import jakarta.persistence.*;
import kr.co.seoulit.his.labimagingservice.common.entity.BaseAuditEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 동의서 (CONSENT)
 * 대응 유스케이스: UC-IMG-05 조영제/침습검사 동의 등록 (Jira ZP2-28)
 *
 * ⚠ 3차 스프린트 골격입니다. PK/감사컬럼만 매핑돼 있고 업무 컬럼은 아직 없습니다.
 *
 * TODO: 아래 컬럼을 DDL 그대로 추가할 것 (lab_imaging_schema_생성.sql 264행)
 *   image_order_id         VARCHAR2(36) NOT NULL  → IMAGE_ORDER 참조 (@ManyToOne)
 *   patient_no             VARCHAR2(20) NOT NULL  (화면 표시용)
 *   patient_id             VARCHAR2(36)           (참조/검증용 — 2026-08-10 추가)
 *   consent_type_code      VARCHAR2(10) NOT NULL  (공통코드 CONSENT_TYPE_CD)
 *   document_template_id   VARCHAR2(36) NOT NULL  (admin DOCUMENT_TEMPLATE 논리 참조, DB FK 아님)
 *   consent_yn             CHAR(1)      NOT NULL  ('Y'/'N', @YnValue 대상)
 *   consent_dt             DATE         NOT NULL
 *   signed_by_name         VARCHAR2(50) NOT NULL
 *   witness_id             VARCHAR2(20) NOT NULL
 *   withdrawn_yn           CHAR(1)      NOT NULL  ('Y'/'N')
 *   withdrawn_at           TIMESTAMP
 *   withdrawn_reason_code  VARCHAR2(10)           (공통코드 CONSENT_WITHDRAW_CD)
 *
 * ⚠ 전자문서(파일)는 저장하지 않는다. 빈 양식은 admin-service 문서양식관리가 소유하고,
 *   이 서비스는 동의 여부·서명자·확인일시 등 업무 데이터만 보유한다. (2026-07-11 문서관리 회의)
 * ⚠ signed_by_name 은 타 서비스 소유 데이터의 스냅샷이 아니라 이 화면에서 직접 입력·확정된
 *   원본 값이므로 저장하는 것이 맞다. (개발표준가이드 14.1 스냅샷 금지의 예외)
 */
@Entity
@Table(name = "CONSENT")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConsentEntity extends BaseAuditEntity {

    @Id
    @Column(name = "consent_id", length = 36, nullable = false, updatable = false)
    private String consentId;

    @PrePersist
    private void generateId() {
        if (this.consentId == null) {
            this.consentId = UUID.randomUUID().toString();
        }
    }
}
