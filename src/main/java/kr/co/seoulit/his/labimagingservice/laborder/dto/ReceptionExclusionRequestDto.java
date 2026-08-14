package kr.co.seoulit.his.labimagingservice.laborder.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 접수 제외 요청 (워크리스트에서 빼기)
 * POST /api/lab-imaging/lab-orders/receptions/{receptionNo}/exclusion
 *
 * ⚠ 대상 접수(receptionNo)는 경로변수로 받으므로 이 DTO 에 담지 않는다.
 *   이미 존재하는 접수를 지목하는 행위라, 재조정 API 와 같은 규칙을 따른다.
 *
 * ⚠ 사유는 필수다. 기간이 지났다고 자동으로 빼지 않고 담당자 판단으로 빼기로 한 설계라,
 *   "왜 뺐는지"가 없으면 다음 담당자가 그 판단을 검증할 수 없다.
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "접수 제외 요청")
public class ReceptionExclusionRequestDto {

    @NotBlank
    @Size(max = 200)
    @Schema(description = "제외 사유", example = "환자 미방문으로 검사 취소",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String exclusionReason;
}
