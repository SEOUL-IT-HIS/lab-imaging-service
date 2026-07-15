package kr.co.seoulit.his.labimagingservice.imagingorder.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 영상 오더 접수 응답
 * API: POST /image-orders
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "영상 오더 접수 응답")
public class ImageOrderCreateResponseDto {

    @Schema(description = "오더ID", example = "7a1c2d3e-4f5a-6b7c-8d9e-0f1a2b3c4d5e")
    private String imageOrderId;

    @Schema(description = "오더번호", example = "IO-2026-000456")
    private String imageOrderNo;

    @Schema(description = "오더상태코드", example = "RECEIVED")
    private String orderStatusCode;

    @Schema(description = "영상접수ID (자동 생성된 IMAGE_RECEPTION)", example = "2b3c4d5e-6f7a-8b9c-0d1e-2f3a4b5c6d7e")
    private String imageReceptionId;

    @Schema(description = "접수번호", example = "IR-2026-000456")
    private String receptionNo;

    @Schema(description = "접수상태코드", example = "ACCEPTED")
    private String receptionStatusCode;
}
