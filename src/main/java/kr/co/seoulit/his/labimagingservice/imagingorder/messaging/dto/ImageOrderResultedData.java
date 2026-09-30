package kr.co.seoulit.his.labimagingservice.imagingorder.messaging.dto;

import kr.co.seoulit.his.labimagingservice.laborder.messaging.dto.LabOrderResultStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 영상오더 접수 결과 payload (LAB → OPD). UC-IMG-01 (5차 Phase 8, D12)
 * 토픽(안): lab.image-order.resulted.v1
 *
 * ⚠ 검사오더 결과(LabOrderResultedData)와 같은 모양이다. labOrderId 자리만 imageOrderId 다.
 * ⚠ 상태값은 검사오더의 ACCEPTED/REJECTED 를 그대로 쓴다(값 두 개뿐인 계약 — 따로 두면 두 enum 이 갈라질 수 있다).
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ImageOrderResultedData {

    private String prescriptionId;
    private LabOrderResultStatus status;
    /** ⚠ ACCEPTED 일 때만 값이 있다 (UUID) */
    private String imageOrderId;
    /** ⚠ REJECTED 일 때만 값이 있다 — 화면에 그대로 보여도 되는 문구 */
    private String reason;

    public static ImageOrderResultedData accepted(String prescriptionId, String imageOrderId) {
        return new ImageOrderResultedData(prescriptionId, LabOrderResultStatus.ACCEPTED, imageOrderId, null);
    }

    public static ImageOrderResultedData rejected(String prescriptionId, String reason) {
        return new ImageOrderResultedData(prescriptionId, LabOrderResultStatus.REJECTED, null, reason);
    }
}
