package kr.co.seoulit.his.labimagingservice.imagingacquisition.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 영상파일 응답 요약 (목록/단건 공용)
 * 대응 유스케이스: UC-IMG-03 (Jira ZP2-110)
 *
 * ⚠ storageKey 는 응답에 넣지 않는다. SeaweedFS 내부 경로라 화면이 알 필요가 없고,
 *   다운로드는 imageFileId 로 여는 다운로드 엔드포인트를 쓴다(ImageFileController).
 *   (patientId 를 화면 응답에서 빼는 ConsentSummaryDto 와 같은 기준)
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "영상파일 응답 요약")
public class ImageFileSummaryDto {

    @Schema(description = "영상파일ID", example = "3f7b1a20-6c2e-4e7a-9e2a-8b1f2c3d4e5f")
    private String imageFileId;

    @Schema(description = "촬영항목ID", example = "9c8b7a6f-1234-4e5f-9a0b-1c2d3e4f5a6b")
    private String imageOrderItemId;

    @Schema(description = "원본 파일명", example = "chest_ct_001.dcm")
    private String fileName;

    @Schema(description = "파일 크기(바이트)", example = "2048576")
    private Long fileSize;

    @Schema(description = "MIME 타입", example = "application/dicom")
    private String contentType;

    @Schema(description = "업로드일시", example = "2026-09-08T10:30:00")
    private LocalDateTime uploadedAt;

    @Schema(description = "업로드자ID", example = "STF00021")
    private String uploadedById;
}
