package kr.co.seoulit.his.labimagingservice.labresult.pathology.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 병리 결과 응답. UC-RST-03
 * ⚠ 첨부는 키 대신 유무·파일명만 내린다. 파일 자체는 GET /{id}/attachment 로 받는다.
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "병리 결과")
public class PathologyResultSummaryDto {

    private String pathologyResultId;
    private String labOrderItemId;
    private String labItemCode;
    private String pathologyTypeCode;
    private String diagnosisCode;
    private String findings;

    @Schema(description = "첨부 유무 Y/N")
    private String attachmentYn;
    @Schema(description = "첨부 원본 파일명 (없으면 null)")
    private String attachmentFileName;
    @Schema(description = "첨부 콘텐츠타입 (image/jpeg, image/png, application/pdf)")
    private String attachmentContentType;

    @Schema(description = "결과상태 (01 등록 / 02 확정)")
    private String resultStatusCode;
    private LocalDateTime recordedAt;
    private String recordedById;
    private LocalDateTime confirmedAt;
    private String confirmedById;
    private LocalDateTime updatedAt;
}
