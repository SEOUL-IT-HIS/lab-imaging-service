package kr.co.seoulit.his.labimagingservice.labspecimen.entity;

/**
 * 검체종류. 공통코드가 아니라 서비스 내부 enum이다(admin 이관 대상 아님, 6차 결정).
 * DB 컬럼(SPECIMEN.specimen_type_code)은 VARCHAR2(10)이고 enum 이름을 그대로 저장한다.
 *
 * ⚠ 6차(2026-09-30)에서 STOOL(대변)·SPUTUM(객담)을 제거하고 TISSUE(조직)·FLUID(체액)를 추가했다.
 *   실제 검사종류(TEST_TYPE_CD 01~08)에 대변·객담 검체를 쓰는 검사가 없고, 조직병리(07)·세포검사(08)에
 *   검체종류를 지정할 수 없던 것이 결함이었다(LAB_TEST_SPECIMEN_RULE 매핑표 기준).
 *   제거 전 SELECT로 기존 데이터를 확인했다 — STOOL 2건, SPUTUM 1건 존재. enum 제거 후 이 값을 가진
 *   SPECIMEN 행을 조회하면 역직렬화 실패(IllegalArgumentException)가 날 수 있다. DDL 적용 시 함께
 *   정리하거나(BLOOD 등으로 UPDATE) 남겨둘지는 사용자 확인 필요 — 이 서비스에서 DML은 하지 않았다.
 */
public enum SpecimenType {
    BLOOD, // 혈액
    URINE, // 소변
    TISSUE, // 조직
    FLUID // 체액
}