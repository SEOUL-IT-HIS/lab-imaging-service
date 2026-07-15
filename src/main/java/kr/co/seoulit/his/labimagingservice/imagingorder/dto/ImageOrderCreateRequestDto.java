package kr.co.seoulit.his.labimagingservice.imagingorder.dto;

import kr.co.seoulit.his.labimagingservice.common.YnValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 영상 오더 접수 요청
 * API: POST /image-orders
 * 대응 유스케이스: UC-IMG-01 영상오더접수 (Jira ZP2-19)
 * 처리 시 IMAGE_ORDER + IMAGE_ORDER_ITEM + IMAGE_RECEPTION 을 한 트랜잭션에서 생성한다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "영상 오더 접수 요청")
public class ImageOrderCreateRequestDto {

    @NotBlank
    @Size(max = 20)
    @Schema(description = "외부시스템 오더 원본 번호 (IMAGE_ORDER.image_order_no, UNIQUE)", example = "EXT-IO-20260715-001", requiredMode = Schema.RequiredMode.REQUIRED)
    private String imageOrderNo;

    @NotBlank
    @Size(max = 10)
    @Schema(description = "연계시스템코드", example = "GR2", requiredMode = Schema.RequiredMode.REQUIRED)
    private String systemCode;

    @NotBlank
    @Size(max = 20)
    @Schema(description = "환자번호 (참조 식별자)", example = "P00012345", requiredMode = Schema.RequiredMode.REQUIRED)
    private String patientNo;

    @Size(max = 20)
    @Schema(description = "처방의번호 (참조 식별자, NULL 허용)", example = "D0032")
    private String physicianNo;

    @NotBlank
    @Size(max = 10)
    @Schema(description = "진료구분코드", example = "OUTPATIENT", requiredMode = Schema.RequiredMode.REQUIRED)
    private String treatTypeCode;

    @NotNull
    @YnValue
    @Schema(description = "응급여부 (Y/N)", example = "N", requiredMode = Schema.RequiredMode.REQUIRED)
    private String urgencyYn;

    @NotBlank
    @Size(max = 20)
    @Schema(description = "접수담당자ID", example = "staff-uuid-001", requiredMode = Schema.RequiredMode.REQUIRED)
    private String receivedById;

    @NotEmpty
    @Valid
    @Schema(description = "촬영항목 목록 (최소 1건)", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<ImageOrderItemRequestDto> orderItems;
}
