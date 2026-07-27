package kr.co.seoulit.his.labimagingservice.labschedule.dto;

import kr.co.seoulit.his.labimagingservice.common.YnValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "검사 일정 등록 요청")
public class LabScheduleCreateRequestDto {

    @NotBlank
    @Size(max = 36)
    @Schema(description = "검사접수ID (LAB_RECEPTION 참조, UUID)", example = "3f7b1a20-6c2e-4e7a-9e2a-8b1f2c3d4e5f")
    private String labReceptionId;

    @NotBlank
    @Size(max = 10)
    @Schema(description = "일정구분코드 (일반/예약 구분, 공통코드값)", example = "GENERAL")
    private String scheduleTypeCode;

    @NotNull
    @Schema(description = "검사 확정일시", example = "2026-07-25T09:30:00")
    private LocalDateTime scheduledAt;

    @NotBlank
    @YnValue
    @Schema(description = "예약여부 (Y/N)", example = "N")
    private String reservationYn;

    @Size(max = 500)
    @Schema(description = "검사 전 준비사항 안내 내용 (선택)", example = "검사 전 8시간 금식 후 방문 바랍니다.")
    private String guidanceNote;

    @NotBlank
    @Size(max = 20)
    @Schema(description = "확정담당자ID (참조 식별자, 원본은 인사/원무 시스템 소유)", example = "STF00021")
    private String confirmedById;

}