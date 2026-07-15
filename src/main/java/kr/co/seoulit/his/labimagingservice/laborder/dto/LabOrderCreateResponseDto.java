package kr.co.seoulit.his.labimagingservice.laborder.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 검사 오더 접수 응답
 * API: POST /lab-orders
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "검사 오더 접수 응답")
public class LabOrderCreateResponseDto {

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
}
