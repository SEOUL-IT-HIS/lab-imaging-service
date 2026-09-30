package kr.co.seoulit.his.labimagingservice.labresult.pathology.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 병리 결과 등록 요청 (multipart 의 "request" 파트, JSON). UC-RST-03 / ZP2-15 (93~98)
 *
 * ⚠ 첨부 파일은 이 DTO 가 아니라 multipart 의 "file" 파트로 받는다(선택). ImageFileUploadRequestDto 처럼
 *   @ModelAttribute 로 받지 않은 이유 — 소견(findings)이 긴 서술형이라 폼 필드보다 JSON 본문이 안전하다
 *   (줄바꿈·특수문자). 대신 요청을 JSON 파트 + 파일 파트 두 개로 나눴다.
 *
 * ⚠ findings 는 화면이 육안/현미경/진단 구획을 제목과 함께 합쳐 보낸 문자열이다(D6). 서버는 그대로 저장한다.
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "병리 결과 등록 요청 (multipart 'request' 파트)")
public class PathologyResultCreateRequestDto {

    @NotBlank
    @Size(max = 36)
    @Schema(description = "대상 검사항목ID — 결과유형이 PATHOLOGY 인 항목만", requiredMode = Schema.RequiredMode.REQUIRED)
    private String labOrderItemId;

    @NotBlank
    @Size(max = 10)
    @Schema(description = "병리유형코드 (PATHOLOGY_TYPE_CD: 01 조직 / 02 세포)", example = "01",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String pathologyTypeCode;

    @Size(max = 20)
    @Schema(description = "병리진단명코드 (PATHOLOGY_DIAGNOSIS_CD) — 선택")
    private String diagnosisCode;

    @NotBlank
    @Size(max = 20000)
    @Schema(description = "소견 — 육안/현미경/진단 구획을 합친 서술형 (D6)", requiredMode = Schema.RequiredMode.REQUIRED)
    private String findings;

    @Size(max = 36)
    @Schema(description = "(로그인 세션이 있으면 무시 — 서버가 로그인 사용자 empId 로 기록) 작성자ID")
    private String recordedById;
}
