package kr.co.seoulit.his.labimagingservice.imaginginterpretation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 영상판독 응답 (워크리스트/상세 공용)
 * 대응 유스케이스: UC-IMG-04 영상판독처리 (Jira ZP2-23)
 *
 * ⚠ 워크리스트 행과 상세 화면이 같은 DTO 를 쓴다. 판독은 촬영항목 1건에 1건뿐이라
 *   목록의 한 줄과 상세의 내용이 같은 모양이기 때문이다. (LabResultSummaryDto 와 같은 이유)
 *
 * ⚠ 배정/동의/일정 등 하위 작업이 여는 화면 이동에 쓰라고 imageOrderItemId 외에
 *   imageOrderId·patientId·urgencyYn 도 함께 담는다. 판독 대상 식별에는 촬영항목이 기준이지만,
 *   화면은 "이 환자, 이 오더"까지 함께 보여줘야 한다. (ImageWorklistItemDto 와 같은 관례)
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "영상판독 응답")
public class ImageReadingSummaryDto {

    @Schema(description = "영상판독ID (배정·소견입력·확정 API 의 경로변수)",
            example = "9a1c7d55-1c1e-4a0a-9a1b-2c3d4e5f6071")
    private String imageReadingId;

    @Schema(description = "촬영항목ID (IMAGE_ORDER_ITEM)", example = "3f7b1a20-6c2e-4e7a-9e2a-8b1f2c3d4e5f")
    private String imageOrderItemId;

    @Schema(description = "촬영항목코드 (공통코드)", example = "CT-CHEST")
    private String imageItemCode;

    @Schema(description = "오더ID (화면 이동/조회용)", example = "9c8b7a6f-1234-4e5f-9a0b-1c2d3e4f5a6b")
    private String imageOrderId;

    @Schema(description = "환자ID (patient-service 내부 식별자)",
            example = "3f7b1a20-6c2e-4e7a-9e2a-8b1f2c3d4e5f")
    private String patientId;

    @Schema(description = "응급여부 (Y/N) — 판독 워크리스트 정렬 기준", example = "N")
    private String urgencyYn;

    @Schema(description = "판독상태코드 (공통코드 READING_STATUS_CD — 01=대기, 02=판독중, 03=완료)",
            example = "01")
    private String readingStatusCode;

    @Schema(description = "배정된 판독의ID (미배정이면 null)", example = "STF00099")
    private String assignedToId;

    @Schema(description = "배정일시 (미배정이면 null)", example = "2026-09-09T09:10:00")
    private LocalDateTime assignedAt;

    @Schema(description = "판독 소견 (미입력이면 null)", example = "Chest CT: No active lung lesion.")
    private String findings;

    @Schema(description = "확정(전자서명)자ID (확정 전이면 null)", example = "STF00099")
    private String signedById;

    @Schema(description = "확정일시 (확정 전이면 null)", example = "2026-09-09T11:00:00")
    private LocalDateTime signedAt;

    @Schema(description = "판독 행 생성일시", example = "2026-09-08T10:30:00")
    private LocalDateTime createdAt;

    @Schema(description = "판독 행 수정일시", example = "2026-09-09T11:00:00")
    private LocalDateTime updatedAt;
}
