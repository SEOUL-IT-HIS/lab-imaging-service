package kr.co.seoulit.his.labimagingservice.laborder.dto;

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
@Schema(description = "검사항목")
public class LabOrderItemRequestDto {

    @NotBlank
    @Size(max = 20)
    @Schema(description = "검사항목코드", example = "01", requiredMode = Schema.RequiredMode.REQUIRED)
    private String labItemCode;
}
