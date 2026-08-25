package kr.co.seoulit.his.labimagingservice.interfacelog.entity;

/**
 * 연계 수신 대상 구분 (INTERFACE_RECEIVE_LOG.order_type_code)
 *
 * ⚠ admin 공통코드가 아니라 서비스 내부 Enum이다.
 *   이 서비스가 받는 오더는 검사/영상 둘뿐이고 운영 중에 값이 늘 성질이 아니다.
 *   (OrderStatus / ReceptionStatus 와 같은 판단 — common/status 패키지 주석 참고)
 *
 * ⚠ DB 컬럼이 VARCHAR2(10)이라 name() 길이가 10자를 넘는 값은 추가할 수 없다.
 */
public enum InterfaceOrderType {

    /** 검사 오더 수신 */
    LAB,

    /** 영상 오더 수신 — 아직 수신 경로가 없다. 검사부터 먼저 붙였다. */
    IMG
}
