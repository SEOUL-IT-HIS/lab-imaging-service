package kr.co.seoulit.his.labimagingservice.imaginginterpretation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 판독 확정(전자서명) 요청
 * 대응 유스케이스: UC-IMG-04 (ZP2-23)
 *
 * ⚠ 확정일시는 받지 않는다. 서버 시각으로 남긴다. (LabResultConfirmRequestDto 와 같은 이유)
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "판독 확정(전자서명) 요청")
public class ImageReadingConfirmRequestDto {

    @NotBlank
    @Size(max = 20)
    @Schema(description = "확정(전자서명)자ID", example = "STF00099",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String signedById;
}
