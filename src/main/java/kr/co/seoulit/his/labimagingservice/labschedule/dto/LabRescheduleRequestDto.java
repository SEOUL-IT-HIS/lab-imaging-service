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
public class LabScheduleRescheduleRequestDto {

    @NotNull
    @Schema
    private LocalDateTime scheduledAt;

    @NotBlank
    @YnValue
    @Schema
    private String reservationYn;

    @Size(max = 500)
    @Schema
    private String guidanceNote;

    @NotBlank
    @Size(max = 20)
    @Schema
    private String confirmedById;

}
