package kr.co.seoulit.his.labimagingservice.billing.messaging.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 검사결과 확정 → 수납 청구 이벤트 payload (LAB → 수납)
 * 토픽: exam-billing-charge
 *
 * ⚠ EventEnvelope 로 감싸지 않는다. OPD 연동(EventEnvelope 봉투)과 달리 수납 쪽 계약은
 *   봉투 구조가 아니라 이 DTO 하나가 그대로 최상위 JSON 이다. (수납팀과 확정된 스펙 — 5차에도 바꾸지 않는다)
 *
 * ⚠ 5차 Phase 5: 발행이 발신 이력(INTERFACE_SEND_LOG) 경유로 바뀌었다. 이력에 JSON 원문을 저장했다가
 *   발행·재발행 때 이 클래스로 되살려 보낸다(InterfaceSendPublisher). 그래서 역직렬화용 @JsonCreator 생성자를
 *   두었다. 코드에서는 여전히 builder 로만 만든다. JSON 모양·필드는 그대로다.
 *   ⚠ 기본 생성자(private) + final 필드 방식은 쓰지 않는다 — Jackson 3 은 그 경우 필드를 채우지 않아,
 *     재발행 메시지가 전부 null 로 나간다(2026-09-29 단위 테스트로 확인). 생성자 인자 이름을 @JsonProperty 로 박아 둔다.
 */
@Getter
public class BillingChargeRequestData {

    /** 환자ID (patient-service 내부 식별자) */
    private final String patientId;

    /** 접수ID (LAB_RECEPTION.lab_reception_id) — 파티션 키로도 쓴다 */
    private final String receptionId;

    /** ⚠ 항상 null. 입원 처방 연동이 아직 없다. 병동 연동이 생기면 그때 채운다. */
    private final String admissionId;

    /** 발생서비스코드. admin SYSTEM_SOURCE_CD 그룹의 "04=Lab System" 확정값이다 (application.properties 참고. 2026-09-16 주석 정정 — "05" 아님) */
    private final String sourceServiceCode;

    /** 이 청구의 근거가 된 우리 쪽 레코드ID. 검사항목ID(LAB_ORDER_ITEM_ID)를 그대로 쓴다 */
    private final String sourceRecordId;

    /** 수가코드. labItemCode → feeCode 매핑 결과 (FeeCodeResolver 참고) */
    private final String feeCode;

    /** 항목명. 참고용 표시 문구라 검증하지 않는다 (LabResultService 호출부 주석 참고) */
    private final String itemName;

    /** ⚠ 항상 "1"(문자열). 검사 1건 = 청구 수량 1건으로 고정한다. */
    private final String quantity;

    /** ⚠ 항상 null. 금액 산정은 이번 연동 범위가 아니다 — 필드는 계약대로 유지만 한다. */
    private final BigDecimal amount;

    @Builder
    @JsonCreator
    private BillingChargeRequestData(@JsonProperty("patientId") String patientId,
                                     @JsonProperty("receptionId") String receptionId,
                                     @JsonProperty("admissionId") String admissionId,
                                     @JsonProperty("sourceServiceCode") String sourceServiceCode,
                                     @JsonProperty("sourceRecordId") String sourceRecordId,
                                     @JsonProperty("feeCode") String feeCode,
                                     @JsonProperty("itemName") String itemName,
                                     @JsonProperty("quantity") String quantity,
                                     @JsonProperty("amount") BigDecimal amount) {
        this.patientId = patientId;
        this.receptionId = receptionId;
        this.admissionId = admissionId;
        this.sourceServiceCode = sourceServiceCode;
        this.sourceRecordId = sourceRecordId;
        this.feeCode = feeCode;
        this.itemName = itemName;
        this.quantity = quantity;
        this.amount = amount;
    }
}
