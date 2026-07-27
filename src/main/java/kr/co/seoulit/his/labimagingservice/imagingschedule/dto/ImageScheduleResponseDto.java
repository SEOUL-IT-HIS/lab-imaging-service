package kr.co.seoulit.his.labimagingservice.imagingschedule.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "영상 촬영 일정 등록 응답")
public class ImageScheduleResponseDto {

    @Schema(description = "영상일정ID (PK, UUID)", example = "a1b2c3d4-1111-2222-3333-444455556666")
    private String imageScheduleId;

    @Schema(description = "영상접수ID (IMAGE_RECEPTION 참조, UUID)", example = "3f7b1a20-6c2e-4e7a-9e2a-8b1f2c3d4e5f")
    private String imageReceptionId;

    @Schema(description = "영상촬영실코드", example = "공통코드")
    private String roomCode;

    @Schema(description = "촬영장비코드", example = "공통코드")
    private String equipmentCode;

    @Schema(description = "영상일정 확정일시", example = "2026-07-25T09:30:00")
    private LocalDateTime scheduledAt;

    @Schema(description = "예약여부 (Y/N)", example = "N")
    private String reservationYn;

    @Schema(description = "금기확인결과코드", example = "공통코드")
    private String contraindicationCheckCode;

    @Schema(description = "금기사항 확인 메모", example = "")
    private String contraindicationNote;

    @Schema(description = "확정담당자ID (참조 식별자)", example = "STF00021")
    private String confirmedById;

    @Schema(description = "최종(현재 유효) 일정 여부. 재조정 이력 중 가장 최신 1건만 Y", example = "Y")
    private String latestYn;

    @Schema(description = "생성일시", example = "2026-07-24T10:15:00")
    private LocalDateTime createdAt;

    @Schema(description = "수정일시", example = "2026-07-24T10:15:00")
    private LocalDateTime updatedAt;
}
