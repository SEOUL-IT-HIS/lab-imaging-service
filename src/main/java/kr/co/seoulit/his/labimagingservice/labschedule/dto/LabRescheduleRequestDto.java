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
@Schema(description = "검사 일정 재등록 요청")
public class LabRescheduleRequestDto {

    @NotNull
    @Schema(description = "재조정된 검사 확정일시", example = "2026-07-26T14:00:00")
    private LocalDateTime scheduledAt;

    @NotBlank
    @YnValue
    @Schema(description = "예약여부 (Y/N)", example = "N")
    private String reservationYn;

    @Size(max = 500)
    @Schema(description = "검사 전 준비사항 안내 내용 (선택)", example = "일정 변경으로 검사 전 8시간 금식 유지 바랍니다.")
    private String guidanceNote;

    @NotBlank
    @Size(max = 20)
    @Schema(description = "재조정 확정담당자ID (참조 식별자)", example = "STF00033")
    private String confirmedById;

}