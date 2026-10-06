package kr.co.seoulit.his.labimagingservice.laborder.messaging.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 처방 비활성화(검사오더 취소) payload (OPD → LAB)
 * 토픽: opd.lab-order.cancelled.v1 (05번 지시서 0-1)
 *
 * ⚠ cancelledItems 는 OPD 가 이미 LAB 에 보낸(SENT/PENDING) 검사 항목만 담는다.
 *   보낸 항목이 없으면 이벤트 자체가 안 온다. 처방당 1회.
 *
 * ⚠ labOrderId 는 OPD 가 우리 ACCEPTED 회신을 받은 항목에만 값이 있고 아니면 null 이다.
 *   매칭 기준은 prescriptionId + itemCode 이고, labOrderId 는 있을 때 교차 확인(WARN 로그)용이다
 *   — 다르다고 거절하지 않는다(LabOrderCancelService.cancel 참고).
 *
 * ⚠ REST 수신 DTO(LabOrderCancelRequestDto)와 필드가 같지만 별도 클래스로 둔다.
 *   LabOrderRequestedData/LabOrderIntakeRequestDto 와 같은 이유 — 한쪽 계약이 바뀌어도
 *   다른 쪽이 흔들리지 않는다. 변환은 LabOrderCancelledConsumer.toCancelRequest 가 담당한다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LabOrderCancelledData {

    /** 처방ID. LAB_ORDER.lab_order_no 와 매칭하는 키다. */
    private String prescriptionId;

    /** 처방의 취소 사유. 200자 초과 시 서비스가 잘라서 저장한다. */
    private String cancelReason;

    /** 취소한 사용자ID(처방의). 표시·기록용 — 직원 검증은 하지 않는다. */
    private String cancelledBy;

    private List<Item> cancelledItems;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {

        /** 검사항목코드 (공통코드 TEST_TYPE_CD) — 매칭 기준. */
        private String itemCode;

        /** 받되 저장하지 않는다. 표시명은 admin 공통코드에서 읽는다. (개발표준가이드 14.1 스냅샷 금지) */
        private String itemName;

        /** 교차 확인용. 우리 오더ID 와 다르면 WARN 로그만 남긴다 — itemCode 가 매칭 기준이다. */
        private String labOrderId;
    }
}
