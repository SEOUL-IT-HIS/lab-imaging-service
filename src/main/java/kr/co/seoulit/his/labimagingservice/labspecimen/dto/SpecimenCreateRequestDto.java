package kr.co.seoulit.his.labimagingservice.labspecimen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kr.co.seoulit.his.labimagingservice.labspecimen.entity.SpecimenType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 검체 채취정보 등록 요청
 * API: POST /api/lab-imaging/specimens
 * 대응 유스케이스: UC-SPC-03 검체식별관리 (Jira ZP2-68)
 *
 * TODO: 필드 추가 — labReceptionId, specimenContainerCode, patientNo, patientId,
 *       collectedAt, collectedById
 *       (@NotBlank/@Size 는 SPECIMEN 테이블 제약과 맞출 것. LabOrderCreateRequestDto 참고)
 * TODO: specimenBarcode 는 요청으로 받지 않고 서버가 채번할지 결정 필요 (ZP2-65 바코드 발행)
 */

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "검체 채취정보 등록 요청")
public class SpecimenCreateRequestDto {

    @NotBlank
    @Size(max = 36)
    @Schema(description = "접수ID", example = "3f7b1a20-6c2e-4e7a-9e2a-8b1f2c3d4e5f", requiredMode = Schema.RequiredMode.REQUIRED)
    private String labReceptionId;

    @NotBlank
    @Size(max = 10)
    @Schema(description = "검체용기코드", example = "튜브")
    private String specimenContainerCode;

    @NotNull
    @Size(max = 10)
    @Schema(description = "검체종류", example = "BLOOD", requiredMode = Schema.RequiredMode.REQUIRED)
    private SpecimenType specimenType;

    @NotBlank
    @Size(max = 20)
    @Schema(description = "환자번호 (화면 표시용 업무번호)", example = "P00012345", requiredMode = Schema.RequiredMode.REQUIRED)
    private String patientNo;

    @NotBlank
    @Size(max = 36)
    @Schema(description = "환자ID (patient-service 내부 식별자, 참조/검증용)", example = "3f7b1a20-6c2e-4e7a-9e2a-8b1f2c3d4e5f", requiredMode = Schema.RequiredMode.REQUIRED)
    private String patientId;

    @NotNull
    @Schema(description = "검체채취일시", example = "2026-07-25T09:30:00")
    private LocalDateTime collectedAt;

    @NotBlank
    @Size(max = 20)
    @Schema(description = "검체채취자ID", example = "STF00021")
    private String collectedById;
}
