package kr.co.seoulit.his.labimagingservice.common.status;

/**
 * 오더상세 항목상태코드 (LAB_ORDER_ITEM / IMAGE_ORDER_ITEM.item_status_code)
 *
 * ⚠ admin 공통코드가 아니라 서비스 내부 Enum으로 관리한다. (2026-08-04 팀 결정)
 *
 * ⚠ 값이 REGISTERED 하나뿐인 건 의도한 것이다.
 *   ERD 테이블정의서에 item_status_code의 값 목록이 정의돼 있지 않고, 현재 코드에서
 *   항목 상태를 바꾸는 지점(일정확정/시행완료/취소 등)이 아직 없다. 쓰이지 않는 상태를
 *   미리 만들어두면 "정의는 됐지만 아무도 안 쓰는 값"이 남아 나중에 실제 워크플로가
 *   설계될 때 오히려 방해가 된다.
 *   검체(UC-SPC-03/04)·결과(UC-RST) 스프린트에서 전이 로직이 생길 때 그 설계에 맞춰 추가한다.
 *
 * ⚠ DB 컬럼이 VARCHAR2(10)이라 name() 길이가 10자를 넘는 값은 추가할 수 없다.
 *   (REGISTERED가 정확히 10자다)
 */
public enum OrderItemStatus {

    /** 등록 — 오더 접수 시 항목이 생성된 상태 */
    REGISTERED
}
