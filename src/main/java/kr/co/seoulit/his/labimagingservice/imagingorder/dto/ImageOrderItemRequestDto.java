package kr.co.seoulit.his.labimagingservice.imagingorder.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "촬영항목")
public class ImageOrderItemRequestDto {

    @NotBlank
    @Size(max = 20)
    @Schema(description = "촬영항목코드", example = "CT_BRAIN", requiredMode = Schema.RequiredMode.REQUIRED)
    private String imageItemCode;
}
