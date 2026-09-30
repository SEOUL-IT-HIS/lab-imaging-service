package kr.co.seoulit.his.labimagingservice.labspecimen.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 검사별 허용 검체·검체용기 기준 (LAB_TEST_SPECIMEN_RULE). 6차 (2026-09-30)
 *
 * ⚠ 읽기 전용 기준(마스터) 테이블이다. 이 서비스에서 등록·수정 API를 두지 않는다(관리 화면은 범위 밖).
 *   그래서 @Builder·PrePersist·수정 메서드가 없다 — 지금 이 엔티티는 SELECT 전용이다.
 * ⚠ PK가 rule_id 지만 UUID 채번 로직이 없다. 초기 데이터를 DDL의 RAWTOHEX(SYS_GUID())로 넣기 때문이다.
 */
@Entity
@Table(name = "LAB_TEST_SPECIMEN_RULE")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LabTestSpecimenRuleEntity {

    @Id
    @Column(name = "rule_id", length = 36, nullable = false, updatable = false)
    private String ruleId;

    /** 검사항목코드 (공통코드 TEST_TYPE_CD) */
    @Column(name = "test_type_code", length = 20, nullable = false)
    private String testTypeCode;

    /** 검체종류 (SpecimenType enum 이름) */
    @Column(name = "specimen_type_code", length = 10, nullable = false)
    private String specimenTypeCode;

    /** 검체용기코드 (공통코드 SPECIMEN_CONTAINER_CD) */
    @Column(name = "specimen_container_code", length = 10, nullable = false)
    private String specimenContainerCode;

    @Column(name = "default_yn", columnDefinition = "CHAR(1)", nullable = false)
    private String defaultYn;

    @Column(name = "use_yn", columnDefinition = "CHAR(1)", nullable = false)
    private String useYn;
}
