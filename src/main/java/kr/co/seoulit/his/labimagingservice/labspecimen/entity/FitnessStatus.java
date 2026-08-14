package kr.co.seoulit.his.labimagingservice.labspecimen.entity;

/**
 * 검체 적합상태코드 (SPECIMEN_ACCEPTANCE.fitness_status_code)
 *
 * ⚠ admin 공통코드가 아니라 서비스 내부 Enum으로 관리한다.
 *   (2026-08-04 공통코드 재분류 회의 결정 — 적합/부적합 이진값이라 운영 중 변할 여지가 없음)
 *   labspecimen 도메인만 쓰므로 common/status 가 아니라 여기 둔다. (SpecimenType 과 같은 기준)
 *
 * ⚠ 상수명을 FIT/UNFIT 으로 둔 이유: 같은 테이블의 unfit_reason_code 컬럼과 어휘를 맞췄다.
 *   "UNFIT 이면 unfit_reason_code 가 필수" 라는 규칙이 이름만으로 읽힌다.
 *
 * ⚠ DB 컬럼이 VARCHAR2(10)이라 name() 길이가 10자를 넘는 값은 추가할 수 없다.
 */
public enum FitnessStatus {

    /** 적합 */
    FIT,

    /** 부적합 — unfit_reason_code 가 함께 있어야 한다 */
    UNFIT
}
