package kr.co.seoulit.his.labimagingservice.labspecimen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kr.co.seoulit.his.labimagingservice.common.YnValue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 검체 인수/적합성 판정 요약 (응답)
 * 대응 유스케이스: UC-SPC-04 검체적합성판정
 */

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "검체 인수/적합성 판정 응답 요약")
public class SpecimenAcceptanceSummaryDto {

    @Schema(description = "검체인수판정ID", example = "3f7b1a20-6c2e-4e7a-9e2a-8b1f2c3d4e5f", requiredMode = Schema.RequiredMode.REQUIRED)
    private String specimenAcceptanceId;

    @Schema(description = "검체ID", example = "3f7b1a20-6c2e-4e7a-9e2a-8b1f2c3d4e5f", requiredMode = Schema.RequiredMode.REQUIRED)
    private String specimenId;

    @Schema(description = "검체 인수 일시", example = "2026-07-25T09:30:00")
    private LocalDateTime acceptedAt;

    @Schema(description = "검체인수자ID", example = "STF00021")
    private String acceptedById;

    @Schema(description = "적합상태코드", example = "적합/부적합")
    private String fitnessStatusCode;

    @Schema(description = "부적합사유코드", example = "검체부족")
    private String unfitReasonCode;

    @Schema(description = "재채취요청여부 (Y/N)", example = "N")
    private String recollectionRequestedYn;
}
