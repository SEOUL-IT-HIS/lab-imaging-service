package kr.co.seoulit.his.labimagingservice.laborder.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 검사오더 취소 요청 (REST) — OPD가 messaging-enabled=false 일 때의 롤백 경로.
 * API: POST /api/lab-imaging/lab-orders/cancel (05번 지시서 2-G)
 *
 * ⚠ Kafka 수신 DTO(LabOrderCancelledData)와 필드가 같지만 별도 클래스로 둔다.
 *   intake 의 Kafka/REST 계약 분리(LabOrderIntakeRequestDto)와 같은 이유다.
 *   두 DTO 를 잇는 건 없다 — 둘 다 LabOrderCancelService.cancel() 에 같은 모양으로 넘긴다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "검사오더 취소 요청 (OPD REST 롤백 경로)")
public class LabOrderCancelRequestDto {

    @NotBlank
    @Size(max = 36)
    @Schema(description = "처방ID — LAB_ORDER.lab_order_no 와 매칭하는 키",
            example = "RX-1", requiredMode = Schema.RequiredMode.REQUIRED)
    private String prescriptionId;

    @Size(max = 200)
    @Schema(description = "처방의 취소 사유", example = "오처방")
    private String cancelReason;

    @Size(max = 36)
    @Schema(description = "취소한 사용자ID(처방의). 표시·기록용 — 직원 검증은 하지 않는다", example = "DOC-1")
    private String cancelledBy;

    @NotEmpty
    @Valid
    @Schema(description = "취소할 검사항목 목록 (최소 1건)", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Item> cancelledItems;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {

        @NotBlank
        @Size(max = 20)
        @Schema(description = "검사항목코드 (공통코드 TEST_TYPE_CD) — 매칭 기준",
                example = "CBC", requiredMode = Schema.RequiredMode.REQUIRED)
        private String itemCode;

        @Size(max = 100)
        @Schema(description = "검사항목명 — 참고용, 저장하지 않음", example = "일반혈액검사")
        private String itemName;

        @Size(max = 36)
        @Schema(description = "교차 확인용 오더ID. 다르면 WARN 로그만 남긴다(itemCode 가 매칭 기준)",
                example = "LO-1")
        private String labOrderId;
    }
}
