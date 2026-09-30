package kr.co.seoulit.his.labimagingservice.imagingorder.messaging.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 영상오더 요청 payload (OPD 처방코어 → LAB). UC-IMG-01 (5차 Phase 8, D12)
 * 토픽(안): opd.image-order.requested.v1
 *
 * ⚠ 처방코어(GR2)와 아직 합의하지 않은 "가정" 규격이다. 검사오더(LabOrderRequestedData)와 같은 모양에
 *   orderItems[].itemCode = IMG_ITEM_CD 로 가정했다. 합의 요청 목록에 이 필드들을 그대로 적는다.
 * ⚠ 검사오더 DTO 를 재사용하지 않고 따로 둔다. 두 규격이 따로 바뀔 수 있어야 한다(검사오더 경로 불변 조건).
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ImageOrderRequestedData {

    /** 처방ID = IMAGE_ORDER.image_order_no (중복 판정 기준, 메시지 키) */
    private String prescriptionId;
    /** 진료건ID — 저장 컬럼 없음, 수신 원문에만 남는다 */
    private String encounterId;
    private String patientId;
    private String doctorId;
    private List<Item> orderItems;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        /** 영상촬영항목코드 (IMG_ITEM_CD) */
        private String itemCode;
        /** 표시명 — 저장하지 않는다(가이드 14.1, 공통코드에서 읽는다) */
        private String itemName;
    }
}
