package kr.co.seoulit.his.labimagingservice.labresult.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 결과항목 입력 양식 1행. 6차 (2026-09-30)
 *
 * ⚠ LabResultItemDto.entryItems 에 담겨 내려간다. 이 검사에 LAB_RESULT_ITEM_RULE 규칙이 있을 때만
 *   채워지고, 화면은 이 목록으로 항목별 입력 칸(코드는 RESULT_ITEM_CD 공통코드로 이름을 붙이고,
 *   단위·참고범위는 읽기 전용으로 보여준 채 값만 입력받는다)을 그린다.
 * ⚠ referenceRange 는 이미 환자 성별을 적용한 값이다(LabResultService.getResultItemsByReceptionNo
 *   가 접수당 1회만 환자 성별을 조회해 붙인다 — 2-4 "환자 성별 조회는 접수당 1회만").
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "결과항목 입력 양식 1행")
public class LabResultEntryItemDto {

    @Schema(description = "결과항목코드 (공통코드 RESULT_ITEM_CD)", example = "02")
    private String resultItemCode;

    @Schema(description = "화면 표시 순번", example = "1")
    private int itemSeq;

    @Schema(description = "기본 단위", example = "x10^3/uL")
    private String defaultUnit;

    @Schema(description = "참고범위 (환자 성별 적용 완료, 판정 근거가 없으면 null)", example = "4.0-10.0")
    private String referenceRange;
}
