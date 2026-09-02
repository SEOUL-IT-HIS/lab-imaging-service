package kr.co.seoulit.his.labimagingservice.labresult.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 일반검사 결과 확정 요청
 * 대응 유스케이스: UC-RST-01 (ZP2-101 등록 → 확정 상태 전이)
 *
 * ⚠ 확정일시는 받지 않는다. 서버 시각으로 남긴다.
 *   "언제 확정했는가"는 확정 행위가 일어난 시점이지 클라이언트가 정할 값이 아니다.
 *
 * ⚠ 필드가 하나뿐이지만 DTO 로 받는다. @RequestParam 으로 받으면
 *   @NotBlank / @Size 검증과 Swagger 문서화가 다른 API 들과 어긋난다.
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "일반검사 결과 확정 요청")
public class LabResultConfirmRequestDto {

    @NotBlank
    @Size(max = 20)
    @Schema(description = "결과 확정자ID", example = "STF00035",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String confirmedById;
}
