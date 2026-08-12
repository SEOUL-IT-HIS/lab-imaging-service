package kr.co.seoulit.his.labimagingservice.labspecimen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import kr.co.seoulit.his.labimagingservice.common.YnValue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 검체 적합성 판정 요청
 * 대응 유스케이스: UC-SPC-04 검체적합성판정 (Jira ZP2-78 판정 로직 및 상태 변경, ZP2-74 부적합 사유·재채취)
 *
 * TODO: 필드 추가 — fitnessStatusCode, unfitReasonCode, recollectionRequestedYn(@YnValue)
 *
 * ⚠ unfitReasonCode 는 부적합일 때만 필수다. Bean Validation 만으로는 이 조건부 필수를
 *   표현할 수 없으니 Service 에서 검증해야 한다. (fitnessStatusCode 가 부적합인데 사유가 없으면 실패)
 */

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "검체 적합성 판정 요청")
public class SpecimenFitnessJudgmentRequestDto {

    @NotBlank
    @Size(max = 10)
    @Schema(description = "적합상태코드", example = "적합/부적합")
    private String fitnessStatusCode;


    @Size(max = 10)
    @Schema(description = "부적합사유코드", example = "검체부족")
    private String unfitReasonCode;

    @NotBlank
    @YnValue
    @Schema(description = "재채취요청여부 (Y/N)", example = "N")
    private String recollectionRequestedYn;


}
