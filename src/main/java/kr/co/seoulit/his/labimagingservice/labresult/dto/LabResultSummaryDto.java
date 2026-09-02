package kr.co.seoulit.his.labimagingservice.labresult.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 일반검사 결과 응답 (목록/단건 공용)
 * 대응 유스케이스: UC-RST-01 일반검사결과등록
 *
 * ⚠ 결과값만 담으면 "무슨 검사의 결과인지" 알 수 없어 검사항목 식별 정보를 함께 담는다.
 *   사람이 결과를 식별하는 값은 UUID 가 아니라 검사항목코드다.
 *   (SpecimenAcceptanceSummaryDto 가 바코드를 함께 담는 것과 같은 이유)
 *
 * ⚠ labResultId 는 담는다. 수정·확정 API 가 이 값을 경로변수로 받는다.
 *   (검체 판정은 재호출할 일이 없어 PK 를 빼지만, 결과는 확정 단계가 남아 있어 필요하다)
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "일반검사 결과 응답")
public class LabResultSummaryDto {

    @Schema(description = "검사결과ID (수정·확정 API 의 경로변수)",
            example = "9a1c7d55-1c1e-4a0a-9a1b-2c3d4e5f6071")
    private String labResultId;

    @Schema(description = "검사항목ID", example = "3f7b1a20-6c2e-4e7a-9e2a-8b1f2c3d4e5f")
    private String labOrderItemId;

    @Schema(description = "검사항목코드 (공통코드 TEST_TYPE_CD)", example = "01")
    private String labItemCode;

    @Schema(description = "검사 결과값", example = "4.2")
    private String resultValue;

    @Schema(description = "결과 단위", example = "mg/dL")
    private String resultUnit;

    @Schema(description = "참고범위", example = "3.5-5.5")
    private String referenceRange;

    @Schema(description = "비정상 여부 (Y/N) — 서버가 참고범위와 비교해 계산한다", example = "N")
    private String abnormalYn;

    @Schema(description = "결과상태코드 (공통코드 RESULT_STATUS_CD — 01=등록, 02=확정)", example = "01")
    private String resultStatusCode;

    @Schema(description = "결과 입력일시", example = "2026-09-02T14:30:00")
    private LocalDateTime recordedAt;

    @Schema(description = "결과 입력자ID", example = "STF00021")
    private String recordedById;

    @Schema(description = "결과 확정일시 (확정 전이면 null)", example = "2026-09-02T16:00:00")
    private LocalDateTime confirmedAt;

    @Schema(description = "결과 확정자ID (확정 전이면 null)", example = "STF00035")
    private String confirmedById;
}
