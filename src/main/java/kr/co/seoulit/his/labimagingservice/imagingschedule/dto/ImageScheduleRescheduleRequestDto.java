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

    /**
     * 재조정할 촬영항목. (2026-09-03 — 일정이 접수 단위에서 항목 단위로 바뀜)
     *
     * ⚠ 대상 접수는 경로변수로 받지만 항목은 본문으로 받는다.
     *   경로에 둘 다 넣으면 /{receptionId}/items/{itemId}/reschedule 이 되는데,
     *   실제로 지목하는 대상은 "그 항목의 최종 일정" 하나라 경로가 길어지기만 한다.
     */
    @NotBlank
    @Size(max = 36)
    @Schema(description = "재조정할 촬영항목ID (IMAGE_ORDER_ITEM)",
            example = "9c8b7a6f-1234-4e5f-9a0b-1c2d3e4f5a6b",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String imageOrderItemId;

    @NotNull
    @Schema(description = "재조정된 촬영 예정일시", example = "2026-07-25T09:30:00")
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
