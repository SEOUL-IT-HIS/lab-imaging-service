package kr.co.seoulit.his.labimagingservice.labresult.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 일반검사 결과 수정 요청 (확정 전에만 허용)
 * 대응 유스케이스: UC-RST-01 (ZP2-101 상태 전이 관리)
 *
 * ⚠ 등록 요청과 필드가 겹치지만 별도 DTO 로 둔다. 수정에는 대상 검사항목도, 입력자도 없다.
 *   - labOrderItemId : 결과가 붙을 항목은 이미 정해져 있다. 옮기는 건 수정이 아니다.
 *   - recordedById   : 처음 입력한 사람을 바꾸는 건 기록 조작이다.
 *   등록 DTO 를 재사용하면 이 두 필드를 "무시한다"고 주석으로만 막게 되고, 언젠가 새어 들어온다.
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "일반검사 결과 수정 요청 (확정 전만 가능)")
public class LabResultUpdateRequestDto {

    @NotBlank
    @Size(max = 200)
    @Schema(description = "검사 결과값 (정량 수치 또는 정성 값)", example = "5.1",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String resultValue;

    @Size(max = 20)
    @Schema(description = "결과 단위", example = "mg/dL")
    private String resultUnit;

    /**
     * ⚠ 수정할 때도 함께 받는다. 참고범위가 바뀌면 정상/비정상 판정도 달라지므로
     *   서버가 abnormalYn 을 다시 계산한다. (LabResultService.decideAbnormalYn)
     */
    @Size(max = 50)
    @Schema(description = "참고범위 (비우면 정상/비정상을 판정하지 않는다)", example = "3.5-5.5")
    private String referenceRange;
}
