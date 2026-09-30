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

    /**
     * 영상 오더 수신 — 5차 Phase 8 ImageOrderRequestedConsumer (app.kafka.image-order.enabled=true 일 때만 동작).
     * ⚠ 작업 문서·DDL 주석에는 "IMAGE"로 적혀 있지만 값은 기존 그대로 IMG 다. 이름을 바꾸면 DB 저장값이 달라진다.
     */
    IMG
}
