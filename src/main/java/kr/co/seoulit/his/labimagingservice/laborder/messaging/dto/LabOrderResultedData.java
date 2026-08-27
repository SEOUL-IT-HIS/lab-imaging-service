package kr.co.seoulit.his.labimagingservice.laborder.messaging.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 검사오더 접수 결과 payload (LAB → OPD)
 * 토픽: lab.lab-order.resulted.v1
 *
 * ⚠ labOrderId 는 UUID 다.
 *   OPD 문서 예시에는 "LAB-ORD-20260827-000123" 같은 형식으로 적혀 있지만,
 *   우리 LAB_ORDER.lab_order_id 는 엔티티의 @PrePersist 에서 만드는 UUID 다.
 *   코어가 이 값을 파싱하려 들면 안 되고 그대로 보관만 하면 된다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class LabOrderResultedData {

    /** 어느 처방에 대한 결과인지. 메시지 키로도 쓴다. */
    private String prescriptionId;

    private LabOrderResultStatus status;

    /** ⚠ ACCEPTED 일 때만 값이 있다. */
    private String labOrderId;

    /**
     * ⚠ REJECTED 일 때만 값이 있다.
     *   OPD 문서상 "화면에 그대로 노출 가능한 문구"라 우리 예외 메시지를 그대로 넘긴다.
     *   메시지에 (labOrderNo=RX-...) 같은 식별자가 붙지만 처방ID는 코어가 이미 아는 값이다.
     */
    private String reason;

    public static LabOrderResultedData accepted(String prescriptionId, String labOrderId) {
        return new LabOrderResultedData(
                prescriptionId, LabOrderResultStatus.ACCEPTED, labOrderId, null);
    }

    public static LabOrderResultedData rejected(String prescriptionId, String reason) {
        return new LabOrderResultedData(
                prescriptionId, LabOrderResultStatus.REJECTED, null, reason);
    }
}
