package kr.co.seoulit.his.labimagingservice.common.status;

/**
 * 오더상세 항목상태코드 (LAB_ORDER_ITEM / IMAGE_ORDER_ITEM.item_status_code)
 *
 * ⚠ admin 공통코드가 아니라 서비스 내부 Enum으로 관리한다. (2026-08-04 팀 결정)
 *
 * ⚠ 오랫동안 값이 REGISTERED 하나뿐이었다. ERD 테이블정의서에 item_status_code의 값
 *   목록이 정의돼 있지 않았고, 항목 상태를 바꾸는 지점이 코드에 없었기 때문이다.
 *   ACQUIRED 가 그 첫 실제 전이(ImageFileService, ZP2-106)다 — 촬영 등록에 성공하면
 *   IMAGE_ORDER_ITEM 을 REGISTERED 에서 ACQUIRED 로 옮긴다.
 *
 *   검사(LAB_ORDER_ITEM) 쪽은 아직 이 전이가 없다. 검사는 결과(LAB_RESULT)가 검사항목과
 *   1:1 이라 "결과 있음/없음" 자체가 진행 상태를 대신하고 있어, item_status_code 를
 *   따로 옮길 필요가 아직 없었다. 검사 쪽에도 전이가 필요해지면 그때 값을 추가한다.
 *
 * ⚠ DB 컬럼이 VARCHAR2(10)이라 name() 길이가 10자를 넘는 값은 추가할 수 없다.
 *   (REGISTERED가 정확히 10자, ACQUIRED는 8자다)
 */
public enum OrderItemStatus {

    /** 등록 — 오더 접수 시 항목이 생성된 상태 */
    REGISTERED,

    /** 촬영완료 — 영상파일이 최소 1건 등록된 상태 (ZP2-106) */
    ACQUIRED
}
