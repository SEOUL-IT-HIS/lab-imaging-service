package kr.co.seoulit.his.labimagingservice.common.status;

/**
 * 접수상태코드 (LAB_RECEPTION / IMAGE_RECEPTION.reception_status_code)
 *
 * ⚠ admin 공통코드가 아니라 서비스 내부 Enum으로 관리한다.
 *   (OrderStatus / OrderItemStatus 와 같은 결정 — 2026-08-04 팀 결정)
 *
 * ⚠ DB 컬럼이 VARCHAR2(10)이라 name() 길이가 10자를 넘는 값은 추가할 수 없다.
 *
 * ── 워크리스트에서의 의미 (2026-08-14)
 *   워크리스트는 "결과가 등록되지 않은 접수는 전부 목록에 있다"가 원칙이라,
 *   접수를 목록에서 빼내는 유일한 수단이 이 상태값이다.
 *
 *   ACCEPTED  : 목록에 남는다 (처리 대상)
 *   EXCLUDED  : 담당자가 판단해 뺀 상태. 사유가 남고 다시 되돌릴 수 있다.
 *
 * ⚠ 앞으로 결과 등록이 구현되면 "결과등록완료" 상태가 추가되는데, 그건 EXCLUDED 와 달리
 *   복구 대상이 아니다. 그래서 같은 값으로 뭉뚱그리지 않고 별도 상수로 추가해야 한다.
 *   (복구 API 가 EXCLUDED 만 되돌리도록 막고 있는 이유 — LabOrderService.restoreReception)
 *
 * ⚠ CANCELLED(05번 지시서, 2026-10-06) — 처방의가 처방을 비활성화(취소)해 끝난 상태.
 *   EXCLUDED 와 다르다: EXCLUDED 는 "담당자가 처리하지 않기로 판단"한 것이라 되돌릴 수 있고,
 *   CANCELLED 는 "처방 자체가 취소"된 것이라 복구 불가·워크리스트 처리 대상 아님이다.
 *   (LabOrderCancelService.cancel, LabOrderService.restoreReception 의 CANCELLED 거절 참고)
 *   DB 컬럼은 VARCHAR2(10) — "CANCELLED"는 9자라 그대로 들어간다.
 */
public enum ReceptionStatus {

    /** 접수완료 — 오더 접수와 함께 접수건이 생성된 상태. 워크리스트에 남는다. */
    ACCEPTED,

    /** 제외 — 담당자가 처리하지 않기로 판단해 워크리스트에서 뺀 상태. 복구 가능. */
    EXCLUDED,

    /** 취소 — 처방의 취소로 종료. 복구 불가, 워크리스트 처리 대상 아님. (05번 지시서) */
    CANCELLED
}
