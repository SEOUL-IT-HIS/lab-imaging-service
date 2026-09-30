package kr.co.seoulit.his.labimagingservice.labresult.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 결과항목(상세) 등록·수정 요청 1건. 6차 — 일반검사 결과항목(상세) (2026-09-30)
 *
 * ⚠ 순번(detailSeq)·단위(resultUnit)·참고범위(referenceRange)·이상여부(abnormalYn)는 요청으로 받지 않는다.
 *   순번은 LAB_RESULT_ITEM_RULE.item_seq 로 서버가 정하고, 단위·참고범위·이상여부는 서버가 계산한다(2-2
 *   "참고범위와 단위는 서버가 정한다"). 클라이언트가 값을 보내도 서버가 무시하므로 애초에 필드를 두지 않는다.
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "결과항목(상세) 1건 — 등록·수정 요청")
public class LabResultDetailRequestDto {

    @NotBlank
    @Size(max = 20)
    @Schema(description = "결과항목코드 (공통코드 RESULT_ITEM_CD)", example = "02",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String resultItemCode;

    @NotBlank
    @Size(max = 200)
    @Schema(description = "결과값 (정량 수치 또는 정성 값)", example = "6.2",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String resultValue;
}
