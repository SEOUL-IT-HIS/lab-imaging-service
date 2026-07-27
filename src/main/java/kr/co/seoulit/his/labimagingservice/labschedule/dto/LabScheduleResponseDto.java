package kr.co.seoulit.his.labimagingservice.labschedule.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "검사 일정 등록 응답")
public class LabScheduleResponseDto {

    @Schema(description = "검사일정ID (PK, UUID)", example = "a1b2c3d4-1111-2222-3333-444455556666")
    private String labScheduleId;

    @Schema(description = "검사접수ID (LAB_RECEPTION 참조, UUID)", example = "3f7b1a20-6c2e-4e7a-9e2a-8b1f2c3d4e5f")
    private String labReceptionId;

    @Schema(description = "일정구분코드 (일반/예약 구분, 공통코드값)", example = "GENERAL")
    private String scheduleTypeCode;

    @Schema(description = "검사 확정일시", example = "2026-07-25T09:30:00")
    private LocalDateTime scheduledAt;

    @Schema(description = "예약여부 (Y/N)", example = "N")
    private String reservationYn;

    @Schema(description = "검사 전 준비사항 안내 내용", example = "검사 전 8시간 금식 후 방문 바랍니다.")
    private String guidanceNote;

    @Schema(description = "확정담당자ID (참조 식별자)", example = "STF00021")
    private String confirmedById;

    @Schema(description = "최종(현재 유효) 일정 여부. 재조정 이력 중 가장 최신 1건만 Y", example = "Y")
    private String latestYn;

    @Schema(description = "생성일시", example = "2026-07-24T10:15:00")
    private LocalDateTime createdAt;

    @Schema(description = "수정일시", example = "2026-07-24T10:15:00")
    private LocalDateTime updatedAt;

}