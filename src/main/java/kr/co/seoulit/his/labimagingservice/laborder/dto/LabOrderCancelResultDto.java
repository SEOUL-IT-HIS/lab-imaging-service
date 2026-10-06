package kr.co.seoulit.his.labimagingservice.laborder.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.status.CancelOutcome;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * 검사오더 취소 처리 결과. (05번 지시서 2-B/2-G)
 *
 * ⚠ Kafka 경로(LabOrderCancelledConsumer)와 REST 경로(LabOrderCancelController)가 같은 DTO 를 쓴다.
 *   REST 응답은 intake 컨트롤러 규칙을 따라 전부 거절(REFUSED)이어도 HTTP 200 으로 내려간다 —
 *   업무 결과이지 오류가 아니다(오더를 못 찾은 경우만 예외, OrderNotYetReceivedException 참고).
 */
@Getter
@AllArgsConstructor
@Schema(description = "검사오더 취소 처리 결과")
public class LabOrderCancelResultDto {

    @Schema(description = "처방ID", example = "RX-1")
    private String prescriptionId;

    @Schema(description = "전체 결과 코드 (LabMessageCode 값)", example = "LAB118")
    private String code;

    @Schema(description = "전체 결과 메시지")
    private String message;

    @Schema(description = "전체 취소/일부 취소/전부 거절")
    private CancelOutcome outcome;

    @Schema(description = "항목별 처리 결과")
    private List<ItemResult> items;

    public static LabOrderCancelResultDto of(String prescriptionId, CancelOutcome outcome, List<ItemResult> items) {
        return new LabOrderCancelResultDto(
                prescriptionId, codeOf(outcome), messageOf(outcome), outcome, items);
    }

    /**
     * 오더를 아직 찾지 못함(LAB117) — REST 경로(LabOrderCancelController)의 404 응답 본문.
     * ⚠ outcome 이 없다(판정 자체가 일어나지 않았다). items 도 빈 목록이다.
     */
    public static LabOrderCancelResultDto notYetReceived(String prescriptionId, String message) {
        return new LabOrderCancelResultDto(prescriptionId, LabMessageCode.LAB117, message, null, List.of());
    }

    private static String codeOf(CancelOutcome outcome) {
        return switch (outcome) {
            case CANCELLED -> LabMessageCode.LAB118;
            case PARTIAL -> LabMessageCode.LAB119;
            case REFUSED -> LabMessageCode.LAB120;
        };
    }

    private static String messageOf(CancelOutcome outcome) {
        return switch (outcome) {
            case CANCELLED -> "요청한 검사오더가 모두 취소되었습니다.";
            case PARTIAL -> "일부 항목만 취소되었습니다. 나머지는 이미 진행되어 취소할 수 없습니다.";
            case REFUSED -> "이미 진행되어 취소할 수 있는 항목이 없습니다.";
        };
    }

    @Getter
    @AllArgsConstructor
    @Schema(description = "검사오더 취소 — 항목별 처리 결과")
    public static class ItemResult {

        @Schema(description = "검사항목코드", example = "CBC")
        private String itemCode;

        @Schema(description = "항목 판정 결과")
        private ItemCancelResult result;

        @Schema(description = "판정 사유")
        private String message;

        public static ItemResult of(String itemCode, ItemCancelResult result, String message) {
            return new ItemResult(itemCode, result, message);
        }
    }
}
