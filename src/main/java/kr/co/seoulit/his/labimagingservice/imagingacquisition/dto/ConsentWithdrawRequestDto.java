package kr.co.seoulit.his.labimagingservice.imagingacquisition.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 동의 철회 요청
 * 대응 유스케이스: UC-IMG-05 (Jira ZP2-84 동의 여부 등록 및 "변경")
 *
 * ⚠ withdrawnYn 은 요청으로 받지 않는다. 철회 API를 호출한 것 자체가 'Y' 를 의미하므로
 *   서버(ConsentEntity#withdraw)가 설정한다. 클라이언트가 'N' 을 보내는 모순을 원천 차단한다.
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "동의 철회 요청")
public class ConsentWithdrawRequestDto {

    @NotBlank
    @Size(max = 10)
    @Schema(description = "철회사유코드 (공통코드 CONSENT_WITHDRAW_CD)", example = "환자거부", requiredMode = Schema.RequiredMode.REQUIRED)
    private String withdrawnReasonCode;

    @NotNull
    @Schema(description = "철회일시", example = "2026-07-25T09:30:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime withdrawnAt;
}
