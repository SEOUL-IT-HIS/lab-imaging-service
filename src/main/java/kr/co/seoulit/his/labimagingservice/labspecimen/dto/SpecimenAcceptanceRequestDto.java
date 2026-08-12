package kr.co.seoulit.his.labimagingservice.labspecimen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 검체 인수 처리 요청 (바코드 스캔 인수)
 * 대응 유스케이스: UC-SPC-04 검체적합성판정 (Jira ZP2-75 바코드 검증 및 인수 처리)
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "검체 인수 처리 요청")
public class SpecimenAcceptanceRequestDto {

    @NotBlank
    @Size(max = 30)
    @Schema(description = "검체바코드", example = "3f7b1a20-6c2e-4e7a-9e2a-8b1f2c3d4e5f", requiredMode = Schema.RequiredMode.REQUIRED)
    private String specimenBarcode;

    @NotNull
    @Schema(description = "검체 인수 일시", example = "2026-07-25T09:30:00")
    private LocalDateTime acceptedAt;

    @NotBlank
    @Size(max = 20)
    @Schema(description = "검체인수자ID", example = "STF00021")
    private String acceptedById;
}
