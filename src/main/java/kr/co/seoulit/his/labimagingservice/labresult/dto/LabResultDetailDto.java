package kr.co.seoulit.his.labimagingservice.labresult.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 결과항목(상세) 응답 1건. 6차 (2026-09-30)
 * 단위·참고범위는 서버가 판정에 실제로 적용한 값이다(요청값이 아니다 — 2-2).
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "결과항목(상세) 응답")
public class LabResultDetailDto {

    @Schema(description = "표시 순번", example = "1")
    private int seq;

    @Schema(description = "결과항목코드 (공통코드 RESULT_ITEM_CD)", example = "02")
    private String resultItemCode;

    @Schema(description = "결과값", example = "6.2")
    private String resultValue;

    @Schema(description = "단위 — 판정에 적용된 값", example = "x10^3/uL")
    private String resultUnit;

    @Schema(description = "참고범위 — 판정에 적용된 값(환자 성별 적용, 없으면 null)", example = "4.0-10.0")
    private String referenceRange;

    @Schema(description = "항목별 이상여부 (Y/N)", example = "N")
    private String abnormalYn;
}
