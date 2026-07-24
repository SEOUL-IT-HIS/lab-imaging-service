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

    @Schema
    private String labScheduleId;

    @Schema
    private String labReceptionId;

    @Schema
    private String scheduleTypeCode;

    @Schema
    private LocalDateTime scheduledAt;

    @Schema
    private String reservationYn;

    @Schema
    private String guidanceNote;

    @Schema
    private String confirmedById;

    @Schema
    private String latestYn;

    @Schema
    private LocalDateTime createdAt;

    @Schema
    private LocalDateTime updatedAt;

}
