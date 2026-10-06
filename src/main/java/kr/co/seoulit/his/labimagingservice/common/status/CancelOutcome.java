package kr.co.seoulit.his.labimagingservice.common.status;

/**
 * 처방 취소 요청의 처리 결과 (LAB_RECEPTION.cancel_outcome)
 * (05번 지시서 Phase 1-B, 2026-10-06)
 *
 * ⚠ 접수상태(ReceptionStatus)와는 다른 축이다. 접수상태는 "지금 이 접수가 어떤 상태인가"이고,
 *   이 값은 "마지막 취소 요청이 어떻게 처리됐는가"의 기록이다. CANCELLED(전체 취소)일 때만
 *   두 값이 같은 뜻을 가리킨다 — PARTIAL/REFUSED 인 접수는 reception_status_code 가
 *   그대로 ACCEPTED/EXCLUDED 로 남아 있는데(이미 일정·검체·결과가 진행 중이라 막을 수 없었다는
 *   뜻), 그 사실을 검사실이 워크리스트 경고로 볼 수 있게 이 컬럼이 따로 남는다.
 *
 * ⚠ DB 컬럼이 VARCHAR2(10)이라 세 값 모두 10자 이내다(CANCELLED 9자, PARTIAL 7자, REFUSED 7자).
 */
public enum CancelOutcome {

    /** 전체 취소 — 오더의 모든 항목이 취소되어 접수도 CANCELLED 로 전환됨. */
    CANCELLED,

    /** 일부만 취소 — 일부 항목은 취소됐지만 나머지는 거절되어 접수는 그대로 남음. */
    PARTIAL,

    /** 전부 거절 — 모든 항목이 이미 진행/완료되어 하나도 취소하지 못함. */
    REFUSED
}
