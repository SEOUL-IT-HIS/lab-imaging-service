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
 * 검체 요약 (목록/단건 공용 응답)
 * 대응 유스케이스: UC-SPC-03 검체식별관리 (Jira ZP2-79 검체 이력 조회)
 *
 * TODO: 필드 추가 — specimenId, specimenBarcode, specimenContainerCode,
 *       patientNo, collectedAt, collectedById
 *       (patientId 는 참조/검증용이라 화면 응답에 넣지 않는 것을 권장 — LabOrderSummaryDto 참고)
 */

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "검체 응답 요약")
public class SpecimenSummaryDto {

    @NotBlank
    @Size(max = 36)
    @Schema(description = "검체ID", example = "3f7b1a20-6c2e-4e7a-9e2a-8b1f2c3d4e5f", requiredMode = Schema.RequiredMode.REQUIRED)
    private String specimenId;

    @NotBlank
    @Size(max = 30)
    @Schema(description = "검체바코드", example = "3f7b1a20-6c2e-4e7a-9e2a-8b1f2c3d4e5f", requiredMode = Schema.RequiredMode.REQUIRED)
    private String specimenBarcode;

    @NotBlank
    @Size(max = 10)
    @Schema(description = "검체용기코드", example = "튜브")
    private String specimenContainerCode;

    @NotBlank
    @Size(max = 20)
    @Schema(description = "환자번호 (화면 표시용 업무번호)", example = "P00012345", requiredMode = Schema.RequiredMode.REQUIRED)
    private String patientNo;

    @NotNull
    @Schema(description = "검체채취일시", example = "2026-07-25T09:30:00")
    private LocalDateTime collectedAt;

    @NotBlank
    @Size(max = 20)
    @Schema(description = "검체채취자ID", example = "STF00021")
    private String collectedById;
}
