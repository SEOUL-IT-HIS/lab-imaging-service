package kr.co.seoulit.his.labimagingservice.imagingconsent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 동의 철회 요청
 * 대응 유스케이스: UC-IMG-05 (Jira ZP2-84 동의 여부 등록 및 "변경") — 5차 Phase 9-3
 *
 * ⚠ withdrawnYn 은 요청으로 받지 않는다. 철회 API를 호출한 것 자체가 'Y' 를 의미하므로
 *   서버(ConsentEntity#withdraw)가 설정한다. 클라이언트가 'N' 을 보내는 모순을 원천 차단한다.
 * ⚠ 철회일시도 받지 않는다(5차에서 제거 — 이 DTO 는 그때까지 쓰는 곳이 없었다). 서버 시각으로 기록한다.
 *   클라이언트 시계를 신뢰하지 않는다(ImageFileService.uploadedAt, LabResultService.recordedAt 과 같은 원칙).
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "동의 철회 요청")
public class ConsentWithdrawRequestDto {

    /** ⚠ 30 인 이유는 ConsentEntity.withdrawnReasonCode 주석 참고 (admin 코드값이 10자를 넘을 수 있다) */
    @NotBlank
    @Size(max = 30)
    @Schema(description = "철회사유코드 (공통코드 CONSENT_WITHDRAW_CD)", example = "01", requiredMode = Schema.RequiredMode.REQUIRED)
    private String withdrawnReasonCode;

    @Size(max = 36)
    @Schema(description = "(로그인 세션이 있으면 무시 — 서버가 로그인 사용자 empId 로 기록) 철회 처리자ID", example = "STF00021")
    private String withdrawnById;
}
