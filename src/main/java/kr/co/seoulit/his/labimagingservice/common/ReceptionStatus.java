package kr.co.seoulit.his.labimagingservice.common;

/**
 * 접수상태코드 (LAB_RECEPTION / IMAGE_RECEPTION.reception_status_code)
 *
 * ⚠ admin 공통코드가 아니라 서비스 내부 Enum으로 관리한다.
 *   (OrderStatus / OrderItemStatus 와 같은 결정 — 2026-08-04 팀 결정)
 *
 * ⚠ 값이 ACCEPTED 하나뿐인 건 OrderItemStatus 와 같은 이유다.
 *   ERD 테이블정의서의 reception_status_code 설명이 "공통코드값 (접수완료 등)" 뿐이라
 *   확정된 값 목록이 없고, 현재 코드에 접수 상태를 바꾸는 지점이 없다.
 *   검체(UC-SPC-03/04)·결과(UC-RST) 스프린트에서 전이 로직이 생길 때 그 설계에 맞춰 추가한다.
 *
 * ⚠ DB 컬럼이 VARCHAR2(10)이라 name() 길이가 10자를 넘는 값은 추가할 수 없다.
 */
public enum ReceptionStatus {

    /** 접수완료 — 오더 접수와 함께 접수건이 생성된 상태 */
    ACCEPTED
}
