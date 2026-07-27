package kr.co.seoulit.his.labimagingservice.imagingschedule.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kr.co.seoulit.his.labimagingservice.common.YnValue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "영상 촬영 일정 등록 요청")
public class ImageScheduleRescheduleRequestDto {

    @NotNull
    @Schema(description = "재조정된 영상 촬영 일정 확정일시", example = "2026-07-25T09:30:00")
    private LocalDateTime scheduledAt;

    @NotBlank
    @Size(max = 10)
    @Schema(description = "영상촬영실코드", example = "공통코드")
    private String roomCode;

    @NotBlank
    @Size(max = 10)
    @Schema(description = "촬영장비코드", example = "공통코드")
    private String equipmentCode;

    @NotBlank
    @YnValue
    @Schema(description = "예약여부 (Y/N)", example = "N")
    private String reservationYn;

    @NotBlank
    @Size(max = 10)
    @Schema(description = "금기확인결과코드", example = "공통코드")
    private String contraindicationCheckCode;

    @Size(max = 500)
    @Schema(description = "금기사항 확인 메모", example = "")
    private String contraindicationNote;

    @NotBlank
    @Size(max = 20)
    @Schema(description = "재조정 확정담당자ID (참조 식별자)", example = "STF00033")
    private String confirmedById;
}
