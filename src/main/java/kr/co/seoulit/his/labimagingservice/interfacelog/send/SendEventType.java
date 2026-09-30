package kr.co.seoulit.his.labimagingservice.interfacelog.send;

/**
 * 발신 이벤트 유형 (INTERFACE_SEND_LOG.event_type_code, 공통코드 SEND_EVENT_TYPE_CD). 5차 Phase 5
 *
 * admin 실측(2026-09-28): 01 = Result Transmission, 02 = Claim Transmission
 * ⚠ 03(판독결과전송, UC-RD-03)은 6차용 예약이라 여기 넣지 않는다. 6차에서 값 등록과 함께 추가하면
 *   발행·재처리 구조는 이 모듈을 그대로 쓴다.
 */
public enum SendEventType {

    /** 검사결과 전송 (UC-RST-06, Phase 6) — EventEnvelope 봉투 */
    RESULT("01"),

    /** 청구 (UC-COM-03) — 봉투 없는 평문 JSON(BillingChargeRequestData), 수납팀 합의 규격 */
    BILLING("02");

    private final String code;

    SendEventType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static SendEventType fromCode(String code) {
        for (SendEventType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("알 수 없는 발신 이벤트 유형: " + code);
    }
}
