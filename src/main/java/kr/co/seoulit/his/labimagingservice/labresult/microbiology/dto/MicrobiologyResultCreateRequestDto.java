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
 * 미생물 결과 등록 요청. UC-RST-02 / ZP2-14 (87~92)
 *
 * ⚠ 결과상태(01 등록)는 요청으로 받지 않는다. 서버가 정한다. (일반검사 결과와 같은 규칙)
 * ⚠ 배양상태와 나머지 필드의 조합 검증(음성이면 균종·감수성 불가 등)은 Bean Validation 이 아니라
 *   서비스가 한다(ZP2-91). 필드 하나만 봐서는 판단할 수 없는 규칙이라서다.
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "미생물 결과 등록 요청")
public class MicrobiologyResultCreateRequestDto {

    @NotBlank
    @Size(max = 36)
    @Schema(description = "대상 검체ID — 적합 판정된 검체만 가능", requiredMode = Schema.RequiredMode.REQUIRED)
    private String specimenId;

    @NotBlank
    @Size(max = 10)
    @Schema(description = "배양상태코드 (CULTURE_STATUS_CD: 01 배양중 / 02 음성 / 03 양성)", example = "03",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String cultureStatusCode;

    @Size(max = 20)
    @Schema(description = "균종코드 (ORGANISM_CD) — 양성일 때만. 동정 전이면 비워 둔다", example = "01")
    private String organismCode;

    @YnValue
    @Schema(description = "원인균 여부 Y/N — 균종이 있을 때만", example = "Y")
    private String causativeYn;

    @Size(max = 4000)
    @Schema(description = "관찰 소견 (서술형)")
    private String observationNote;

    @Valid
    @Schema(description = "항생제 감수성 목록 — 양성 + 균종이 있을 때만. 항생제 중복 불가")
    private List<MicrobiologySusceptibilityDto> susceptibilities;

    @Size(max = 36)
    @Schema(description = "(로그인 세션이 있으면 무시 — 서버가 로그인 사용자 empId 로 기록) 결과 입력자ID")
    private String recordedById;
}
