package kr.co.seoulit.his.labimagingservice.laborder.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 검사 오더 접수 응답
 * API: POST /lab-orders
 *
 * ⚠ 이름은 Summary 지만 이제 접수 생성 응답 전용이다. (2026-08-14)
 *   목록 조회에서도 쓰이던 시절의 이름이라, 목록이 워크리스트로 옮겨간 뒤로는
 *   생성 응답 한 곳에서만 쓴다. 이름 정리는 다음 기회에.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "검사 오더 접수 응답")
public class LabOrderSummaryDto {

    @Schema(description = "오더ID", example = "3f7b1a20-6c2e-4e7a-9e2a-8b1f2c3d4e5f")
    private String labOrderId;

    @Schema(description = "오더번호", example = "LO-2026-000123")
    private String labOrderNo;

    @Schema(description = "오더상태코드", example = "RECEIVED")
    private String orderStatusCode;

    @Schema(description = "접수ID (자동 생성된 LAB_RECEPTION)", example = "9c8b7a6f-1234-4e5f-9a0b-1c2d3e4f5a6b")
    private String labReceptionId;

    @Schema(description = "접수번호", example = "LR-2026-000123")
    private String receptionNo;

    @Schema(description = "접수상태코드", example = "ACCEPTED")
    private String receptionStatusCode;

    /*
     * scheduledAt 은 삭제했다. (2026-08-14)
     * 접수를 막 만든 시점에는 일정이 있을 수 없어 항상 null 이었고,
     * 값이 채워지던 유일한 경로(목록 조회)는 워크리스트로 옮겨갔다.
     */
}
