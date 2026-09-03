package kr.co.seoulit.his.labimagingservice.labresult.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 접수 1건의 검사항목 + 그 항목의 결과 (결과 등록 화면용)
 * 대응 유스케이스: UC-RST-01 (ZP2-104 화면 연동)
 *
 * ⚠ 이 DTO 가 필요한 이유 —
 *   결과 등록은 검사항목(LAB_ORDER_ITEM) 단위인데, 화면은 접수를 골라서 들어온다.
 *   접수 상세(LabReceptionDetailDto)는 labItemCodes(코드 문자열)만 주고 항목ID를 주지 않아
 *   그것만으로는 어느 항목에 결과를 등록할지 지목할 수 없다.
 *
 * ⚠ 항목과 결과를 한 행으로 묶어 내려준다. 항목 목록과 결과 목록을 따로 받아
 *   화면에서 맞추게 하면, 그 맞추는 규칙이 화면마다 생긴다.
 *
 * ⚠ result 가 null 이면 아직 결과가 등록되지 않은 항목이다.
 *   (검체 목록에서 fitnessStatus 가 null 이면 미판정인 것과 같은 규약)
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "검사항목 + 결과 (결과 등록 화면용)")
public class LabResultItemDto {

    @Schema(description = "검사항목ID — 결과 등록 요청에 담는다",
            example = "3f7b1a20-6c2e-4e7a-9e2a-8b1f2c3d4e5f")
    private String labOrderItemId;

    @Schema(description = "검사항목코드 (공통코드 TEST_TYPE_CD)", example = "01")
    private String labItemCode;

    @Schema(description = "등록된 결과. 아직 등록 전이면 null")
    private LabResultSummaryDto result;
}
