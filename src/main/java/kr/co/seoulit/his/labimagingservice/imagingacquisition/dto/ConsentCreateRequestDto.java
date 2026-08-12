package kr.co.seoulit.his.labimagingservice.imagingacquisition.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kr.co.seoulit.his.labimagingservice.common.YnValue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 조영제/침습검사 동의 등록 요청
 * API: POST /api/lab-imaging/consents
 * 대응 유스케이스: UC-IMG-05 (Jira ZP2-84 동의 여부 등록 및 변경, ZP2-83 필수값·유효성 검증)
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "조영제/침습검사 동의 등록 요청")
public class ConsentCreateRequestDto {

    @NotBlank
    @Size(max = 36)
    @Schema(description = "영상오더ID (IMAGE_ORDER 참조, UUID)", example = "3f7b1a20-6c2e-4e7a-9e2a-8b1f2c3d4e5f", requiredMode = Schema.RequiredMode.REQUIRED)
    private String imageOrderId;

    @NotBlank
    @Size(max = 20)
    @Schema(description = "환자번호 (화면 표시용 업무번호)", example = "P00012345", requiredMode = Schema.RequiredMode.REQUIRED)
    private String patientNo;

    @NotBlank
    @Size(max = 36)
    @Schema(description = "환자ID (patient-service 내부 식별자, 참조/검증용)", example = "3f7b1a20-6c2e-4e7a-9e2a-8b1f2c3d4e5f", requiredMode = Schema.RequiredMode.REQUIRED)
    private String patientId;

    @NotBlank
    @Size(max = 10)
    @Schema(description = "동의서유형코드 (공통코드 CONSENT_TYPE_CD)", example = "조영제사용", requiredMode = Schema.RequiredMode.REQUIRED)
    private String consentTypeCode;

    @NotBlank
    @Size(max = 36)
    @Schema(description = "동의서양식ID (admin-service DOCUMENT_TEMPLATE 논리 참조)", example = "d0a1b2c3-4d5e-6f70-8192-a3b4c5d6e7f8", requiredMode = Schema.RequiredMode.REQUIRED)
    private String documentTemplateId;

    @NotBlank
    @YnValue
    @Schema(description = "동의여부 (Y/N)", example = "Y", requiredMode = Schema.RequiredMode.REQUIRED)
    private String consentYn;

    @NotNull
    @Schema(description = "동의일자", example = "2026-07-25", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate consentDt;

    @NotBlank
    @Size(max = 50)
    @Schema(description = "서명자명 (환자 또는 법정대리인, 이 화면에서 직접 입력)", example = "홍길동", requiredMode = Schema.RequiredMode.REQUIRED)
    private String signedByName;

    @NotBlank
    @Size(max = 20)
    @Schema(description = "확인자ID", example = "STF00021", requiredMode = Schema.RequiredMode.REQUIRED)
    private String witnessId;
}
