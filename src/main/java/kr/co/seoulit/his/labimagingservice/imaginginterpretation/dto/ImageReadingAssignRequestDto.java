package kr.co.seoulit.his.labimagingservice.imaginginterpretation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 판독 담당자 배정 요청
 * 대응 유스케이스: UC-IMG-04 (ZP2-23)
 *
 * ⚠ assignedToId 는 참조 식별자다. 직원 서비스에 존재 여부를 묻지 않는다.
 *   (LabResultCreateRequestDto.recordedById 와 같은 취급)
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "판독 담당자 배정 요청")
public class ImageReadingAssignRequestDto {

    @NotBlank
    @Size(max = 20)
    @Schema(description = "배정할 판독의ID", example = "STF00099",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String assignedToId;
}
