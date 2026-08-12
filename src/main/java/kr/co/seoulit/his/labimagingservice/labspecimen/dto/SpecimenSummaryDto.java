package kr.co.seoulit.his.labimagingservice.labspecimen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import kr.co.seoulit.his.labimagingservice.labspecimen.entity.SpecimenType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 검체 요약 (목록/단건 공용 응답)
 * 대응 유스케이스: UC-SPC-03 검체식별관리 / UC-SPC-04 검체적합성판정(ZP2-79 미판정 검체 목록 조회)
 */

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "검체 응답 요약")
public class SpecimenSummaryDto {

    @Schema(description = "접수ID", example = "3f7b1a20-6c2e-4e7a-9e2a-8b1f2c3d4e5f", requiredMode = Schema.RequiredMode.REQUIRED)
    private String labReceptionId;

    @Schema(description = "검체ID", example = "3f7b1a20-6c2e-4e7a-9e2a-8b1f2c3d4e5f", requiredMode = Schema.RequiredMode.REQUIRED)
    private String specimenId;

    @Schema(description = "검체바코드", example = "3f7b1a20-6c2e-4e7a-9e2a-8b1f2c3d4e5f", requiredMode = Schema.RequiredMode.REQUIRED)
    private String specimenBarcode;

    @Schema(description = "검체종류", example = "BLOOD", requiredMode = Schema.RequiredMode.REQUIRED)
    private SpecimenType specimenType;

    @Schema(description = "검체용기코드", example = "튜브", requiredMode = Schema.RequiredMode.REQUIRED)
    private String specimenContainerCode;

    @Schema(description = "환자번호 (화면 표시용 업무번호)", example = "P00012345", requiredMode = Schema.RequiredMode.REQUIRED)
    private String patientNo;

    @Schema(description = "검체채취일시", example = "2026-07-25T09:30:00")
    private LocalDateTime collectedAt;

    @Schema(description = "검체채취자ID", example = "STF00021")
    private String collectedById;
}
