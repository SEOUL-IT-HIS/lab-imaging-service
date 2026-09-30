package kr.co.seoulit.his.labimagingservice.labresult.microbiology.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import kr.co.seoulit.his.labimagingservice.common.YnValue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 미생물 결과 수정 요청 (확정 전만). 중간보고 → 갱신 → 최종보고 흐름에서 쓴다. (D4)
 *
 * ⚠ 검체와 입력자는 바꿀 수 없다. 최초 입력자를 바꾸는 건 기록 조작이다(일반검사 수정과 같은 규칙).
 * ⚠ 감수성 목록은 통째로 교체한다. 보내지 않으면(null) 빈 목록으로 본다.
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "미생물 결과 수정 요청 (확정 전만)")
public class MicrobiologyResultUpdateRequestDto {

    @NotBlank
    @Size(max = 10)
    @Schema(description = "배양상태코드 (CULTURE_STATUS_CD)", example = "03", requiredMode = Schema.RequiredMode.REQUIRED)
    private String cultureStatusCode;

    @Size(max = 20)
    @Schema(description = "균종코드 (ORGANISM_CD) — 양성일 때만")
    private String organismCode;

    @YnValue
    @Schema(description = "원인균 여부 Y/N — 균종이 있을 때만")
    private String causativeYn;

    @Size(max = 4000)
    @Schema(description = "관찰 소견")
    private String observationNote;

    @Valid
    @Schema(description = "항생제 감수성 목록 (통째 교체)")
    private List<MicrobiologySusceptibilityDto> susceptibilities;
}
