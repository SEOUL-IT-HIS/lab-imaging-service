package kr.co.seoulit.his.labimagingservice.common.status;

/**
 * 오더상태코드 (LAB_ORDER / IMAGE_ORDER.order_status_code)
 *
 * ⚠ admin 공통코드가 아니라 서비스 내부 Enum으로 관리한다. (2026-08-04 팀 결정)
 *   값이 운영 중 추가/변경될 성질이 아니고, 이 서비스 안에서만 전이가 일어나기 때문이다.
 *   검사/영상 오더의 상태 흐름이 동일해서 두 도메인이 이 Enum 하나를 공유한다.
 *   (흐름이 갈라지면 그때 도메인별로 분리한다)
 *
 * 값 구성은 ERD 테이블정의서의 order_status_code 설명 "수신/처리완료/오류"를 그대로 따랐다.
 * 취소(UC-SPC-06)는 아직 ERD에 취소 관련 테이블·컬럼이 없어 포함하지 않았다.
 *
 * ⚠ DB 컬럼이 VARCHAR2(10)이라 name() 길이가 10자를 넘는 값은 추가할 수 없다.
 *   (ddl-auto=validate 라서 컬럼을 늘리려면 DB 변경이 선행돼야 한다)
 */
public enum OrderStatus {

    /** 수신 — 외부 시스템 오더를 받아 접수를 생성한 상태 */
    RECEIVED,

    /** 처리완료 */
    COMPLETED,

    /** 오류 */
    ERROR
}
