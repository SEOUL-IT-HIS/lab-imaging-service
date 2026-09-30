package kr.co.seoulit.his.labimagingservice.labresult.microbiology.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 항생제 감수성 1건 (요청·응답 공용). UC-RST-02 / ZP2-14
 *
 * ⚠ 요청과 응답 모양이 같아 한 클래스로 둔다. 응답에 ID 를 내리지 않는 이유 —
 *   수정은 목록 통째 교체라 화면이 행 ID 를 알 필요가 없다.
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "항생제 감수성 결과 1건")
public class MicrobiologySusceptibilityDto {

    @NotBlank
    @Size(max = 20)
    @Schema(description = "항생제코드 (공통코드 ANTIBIOTIC_CD)", example = "01",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String antibioticCode;

    @NotBlank
    @Size(max = 10)
    @Schema(description = "감수성판정코드 (공통코드 SUSCEPTIBILITY_RESULT_CD: 01 S / 02 I / 03 R)", example = "01",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String susceptibilityResultCode;
}
