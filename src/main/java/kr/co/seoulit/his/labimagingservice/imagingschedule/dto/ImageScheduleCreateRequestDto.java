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
public class ImageScheduleCreateRequestDto {

    @NotBlank
    @Size(max = 36)
    @Schema(description = "영상접수ID (IMAGE_RECEPTION 참조, UUID)", example = "3f7b1a20-6c2e-4e7a-9e2a-8b1f2c3d4e5f")
    private String imageReceptionId;

    /**
     * 일정을 잡을 촬영항목. (2026-09-03 — 일정이 접수 단위에서 항목 단위로 바뀜)
     *
     * ⚠ 접수ID 만으로는 어느 촬영의 일정인지 정할 수 없다.
     *   CT·MRI·초음파는 서로 다른 방과 장비를 쓰고 같은 시각에 할 수도 없다.
     */
    @NotBlank
    @Size(max = 36)
    @Schema(description = "대상 촬영항목ID (IMAGE_ORDER_ITEM)",
            example = "9c8b7a6f-1234-4e5f-9a0b-1c2d3e4f5a6b",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String imageOrderItemId;

    @NotBlank
    @Size(max = 10)
    @Schema(description = "영상촬영실코드", example = "공통코드")
    private String roomCode;

    @NotBlank
    @Size(max = 10)
    @Schema(description = "촬영장비코드", example = "공통코드")
    private String equipmentCode;

    @NotNull
    @Schema(description = "촬영 예정일시 (검사를 시행할 일시. 확정한 시각은 created_at)", example = "2026-07-25T09:30:00")
    private LocalDateTime scheduledAt;

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
    @Schema(description = "확정담당자ID (참조 식별자, 원본은 인사/원무 시스템 소유)", example = "STF00021")
    private String confirmedById;
}
