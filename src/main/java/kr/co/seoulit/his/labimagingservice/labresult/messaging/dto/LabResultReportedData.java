package kr.co.seoulit.his.labimagingservice.labresult.messaging.dto;

import lombok.Getter;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 검사결과 보고 이벤트 payload (EventEnvelope.data). UC-RST-06 / ZP2-17 (5차 Phase 6)
 * 토픽: lab.lab-result.reported.v1 / eventType: LabResultReported — 처방코어(GR2) 제안 형식(2026-09-29)에 맞췄다.
 *
 * ── 이벤트 단위 (D10 유지): 결과 1건 확정 = 이벤트 1건. items 에는 이번에 확정된 항목 1건이 들어간다.
 *   오더의 항목들을 모았다가 한 번에 보내지 않는 이유 — 미생물 배양은 며칠 걸린다. 모아서 보내면 그동안
 *   이미 나온 일반검사 결과까지 처방 화면에 늦게 뜬다. 오더 전체 완료 여부는 confirmedItemCount == totalItemCount 로 알린다.
 *   (나중에 묶음 발행으로 바꿔도 items 가 배열이라 수신측 계약은 그대로다)
 * ── resultStatus: 지금은 확정(02)만 발행하므로 항상 FINAL. PRELIMINARY 는 예약값(미생물 중간보고 발행을 합의하면 사용).
 *
 * ⚠ 항목명(itemName)은 채우지 않는다(null). 표시명은 admin 공통코드(TEST_TYPE_CD) 소유라 우리 이벤트·발신 이력에
 *   복사해 두면 이름이 바뀔 때 어긋난다(개발표준가이드 14.1 스냅샷 금지). 수신측이 itemCode 로 풀어 쓴다.
 *   필드는 제안 형식에 맞춰 남겨 둔다.
 * ⚠ 환자명·첨부 파일 자체는 싣지 않는다.
 */
@Getter
@Builder
public class LabResultReportedData {

    /** 결과 상태 — 제안 형식의 값 */
    public static final String STATUS_FINAL = "FINAL";
    public static final String STATUS_PRELIMINARY = "PRELIMINARY";

    // ---- 상관키 ----
    /** = LAB_ORDER.lab_order_no = 처방코어 prescriptionId. 메시지 키이기도 하다 */
    private final String prescriptionId;
    /** 접수 결과(ACCEPTED)로 회신했던 검사오더ID — 처방코어 상관키 */
    private final String labOrderId;

    // ---- 결과 ----
    /** FINAL / PRELIMINARY */
    private final String resultStatus;
    /** 결과 확정 일시 (+09:00) */
    private final OffsetDateTime reportedAt;
    private final List<Item> items;

    // ---- 부가 정보 (제안 형식 외 — 수신측 필터링·진행도용, 무시해도 된다) ----
    private final String receptionNo;
    private final String patientId;
    /** 요청 출처 (SYSTEM_SOURCE_CD) */
    private final String systemCode;
    /** 진료구분 (RCPT_TYPE_CD) */
    private final String treatTypeCode;
    /** 응급 여부 Y/N */
    private final String urgencyYn;
    /** 이 오더의 확정된 항목 수 (이번 확정 포함) */
    private final int confirmedItemCount;
    /** 이 오더의 전체 항목 수 — confirmed == total 이면 오더 전체 확정 */
    private final int totalItemCount;

    @Getter
    @Builder
    public static class Item {
        /** 검사항목코드 (TEST_TYPE_CD) */
        private final String itemCode;
        /** 항상 null — 클래스 주석 참고 (수신측이 itemCode 로 공통코드 조회) */
        private final String itemName;
        /** 일반검사 결과값(문자열). 미생물·병리는 null — 아래 유형별 필드를 본다 */
        private final String resultValue;
        private final String unit;
        private final String referenceRange;
        /** 판정: N=정상 / A=이상. 결과 입력에 H/L 구분이 없어 두 값만 쓴다. 미생물·병리는 null */
        private final String abnormalFlag;

        // ---- 부가 (제안 형식 외) ----
        /** GENERAL / MICROBIOLOGY / PATHOLOGY */
        private final String resultType;
        private final String labOrderItemId;
        private final String resultId;
        private final String confirmedById;

        // 미생물
        private final String cultureStatusCode;
        private final String organismCode;
        private final String causativeYn;
        private final List<Susceptibility> susceptibilities;

        // 병리
        private final String pathologyTypeCode;
        private final String diagnosisCode;
        private final String findings;
        private final String attachmentYn;
    }

    @Getter
    @Builder
    public static class Susceptibility {
        private final String antibioticCode;
        private final String susceptibilityResultCode;
    }

    /** LAB_RESULT.abnormal_yn(Y/N) → abnormalFlag(A/N). 값이 없으면 null */
    public static String toAbnormalFlag(String abnormalYn) {
        if ("Y".equals(abnormalYn)) {
            return "A";
        }
        return "N".equals(abnormalYn) ? "N" : null;
    }
}
