package kr.co.seoulit.his.labimagingservice.labresult.pathology.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 병리 결과 수정 요청 (multipart 의 "request" 파트, 확정 전만).
 *
 * ⚠ 검사항목·작성자는 바꿀 수 없다(일반검사 수정과 같은 규칙).
 * ⚠ "file" 파트를 같이 보내면 첨부를 교체한다. 첨부 등록이 실패했을 때 "재등록"(UC-RST-03 예외흐름)이 이 경로다.
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "병리 결과 수정 요청 (multipart 'request' 파트)")
public class PathologyResultUpdateRequestDto {

    @NotBlank
    @Size(max = 10)
    @Schema(description = "병리유형코드 (PATHOLOGY_TYPE_CD)", requiredMode = Schema.RequiredMode.REQUIRED)
    private String pathologyTypeCode;

    @Size(max = 20)
    @Schema(description = "병리진단명코드 (PATHOLOGY_DIAGNOSIS_CD) — 선택")
    private String diagnosisCode;

    @NotBlank
    @Size(max = 20000)
    @Schema(description = "소견 (D6)", requiredMode = Schema.RequiredMode.REQUIRED)
    private String findings;
}
