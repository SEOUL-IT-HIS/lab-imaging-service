package kr.co.seoulit.his.labimagingservice.labresult.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 일반검사 결과 등록 요청 (수기 입력)
 * 대응 유스케이스: UC-RST-01 일반검사결과등록 (ZP2-100 수기 입력, ZP2-103 필수값/유효성)
 *
 * ⚠ 서버가 정하는 값은 받지 않는다. 받으면 같은 검사가 보내는 쪽에 따라 다르게 저장된다.
 *   - abnormalYn        : 참고범위와 결과값을 비교해 서버가 계산한다 (ZP2-99)
 *   - resultStatusCode  : 등록은 언제나 "01"(등록)에서 시작한다. 확정은 별도 API 다.
 *   - recordedAt        : 서버 시각. 클라이언트 시계를 신뢰하지 않는다.
 *   - confirmedAt / confirmedById : 확정 API 만 채운다.
 *
 * ⚠ recordedById 는 참조 식별자다. 직원 서비스에 존재 여부를 물어보지 않는다.
 *   (LAB_RECEPTION.received_by_id, SPECIMEN_ACCEPTANCE.accepted_by_id 와 같은 취급)
 *
 * ⚠ 대상 검사항목(labOrderItemId)은 본문으로 받는다. 아직 결과가 없는 항목에
 *   결과를 "새로 만드는" 행위라, 기존 리소스를 지목하는 경로변수 방식과는 다르다.
 *   (검체 등록 SpecimenCreateRequestDto 가 labReceptionId 를 본문에 담는 것과 같은 이유)
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "일반검사 결과 등록 요청")
public class LabResultCreateRequestDto {

    @NotBlank
    @Size(max = 36)
    @Schema(description = "대상 검사항목ID (LAB_ORDER_ITEM)",
            example = "3f7b1a20-6c2e-4e7a-9e2a-8b1f2c3d4e5f",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String labOrderItemId;

    /**
     * ⚠ 6차부터 조건부 필수다(@NotBlank 를 뗐다). 이 검사에 결과항목(상세) 규칙이 있으면
     *   여기가 아니라 details 로 입력하고 이 필드는 비운다 — 둘 다 비어 있거나 둘 다 채워지면
     *   서비스가 LAB099/LAB102 로 거절한다(2-2). 규칙이 없는 검사(기존 방식)는 지금까지처럼 필수다.
     *   숫자로 강제하지 않는 이유는 그대로다 — 정성검사 결과("음성")가 같은 자리에 들어온다.
     */
    @Size(max = 200)
    @Schema(description = "검사 결과값 (정량 수치 또는 정성 값). 이 검사에 결과항목이 있으면 비워 두고 details 를 쓴다",
            example = "4.2")
    private String resultValue;

    @Size(max = 20)
    @Schema(description = "결과 단위 (정성검사는 비워 둔다)", example = "mg/dL")
    private String resultUnit;

    @Size(max = 50)
    @Schema(description = "참고범위 — 정상으로 보는 값. 정량은 \"3.5-5.5\", 정성은 \"음성\" 형태. "
            + "쉼표로 여러 정상값을 줄 수 있다(\"음성,정상\"). 비워 두면 정상/비정상을 판정하지 않는다.",
            example = "3.5-5.5")
    private String referenceRange;

    @Size(max = 36)
    @Schema(description = "(로그인 세션이 있으면 무시 — 서버가 로그인 사용자 empId 로 기록) 결과 입력자ID", example = "STF00021")
    private String recordedById;

    /**
     * 결과항목(상세) 목록. 6차 — 이 검사에 LAB_RESULT_ITEM_RULE 규칙이 있으면 필수, 없으면 비워야 한다.
     * 규칙에 정의된 항목을 전부 채울 필요는 없다(최소 1개, 상한 4개 — LabResultService.MAX_DETAILS).
     */
    @Valid
    @Schema(description = "결과항목(상세) 목록 — 이 검사에 결과항목이 있으면 1~4개 필수, 없으면 비워 둔다")
    private List<LabResultDetailRequestDto> details;
}
