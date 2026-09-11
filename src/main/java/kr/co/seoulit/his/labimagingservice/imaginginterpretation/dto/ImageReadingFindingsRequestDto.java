package kr.co.seoulit.his.labimagingservice.imaginginterpretation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 판독 소견 입력/수정 요청 (확정 전에만 허용)
 * 대응 유스케이스: UC-IMG-04 (ZP2-23)
 *
 * ⚠ 길이 제한(@Size)을 두지 않는다. findings 는 CLOB 이라 서술형 소견 전문이 그대로 들어온다.
 *   (LabResultUpdateRequestDto.resultValue 가 VARCHAR2 라 @Size(max=200) 을 두는 것과 다르다)
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "판독 소견 입력/수정 요청 (확정 전만 가능)")
public class ImageReadingFindingsRequestDto {

    @NotBlank
    @Schema(description = "판독 소견", example = "Chest CT: No active lung lesion. Follow-up in 6 months.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String findings;
}
