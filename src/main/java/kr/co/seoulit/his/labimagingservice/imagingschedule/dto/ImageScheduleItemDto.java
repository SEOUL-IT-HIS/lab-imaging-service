package kr.co.seoulit.his.labimagingservice.imagingschedule.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 접수 1건의 촬영항목 + 그 항목의 최종 일정 (일정 등록 화면용)
 * 대응 유스케이스: UC-IMG-02 영상촬영일정관리
 *
 * ⚠ 이 DTO 가 필요한 이유 —
 *   일정 등록은 촬영항목(IMAGE_ORDER_ITEM) 단위인데 화면은 접수를 골라서 들어온다.
 *   접수 상세(ImageReceptionDetailDto)는 imageItemCodes(코드 문자열)만 주고 항목ID를 주지 않아
 *   그것만으로는 어느 항목에 일정을 잡을지 지목할 수 없다.
 *   (검사결과 등록 화면의 LabResultItemDto 와 같은 이유·같은 모양)
 *
 * ⚠ schedule 이 null 이면 아직 일정이 잡히지 않은 항목이다. 그게 등록 대상이다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "촬영항목 + 최종 일정 (일정 등록 화면용)")
public class ImageScheduleItemDto {

    @Schema(description = "촬영항목ID — 일정 등록 요청에 담는다",
            example = "9c8b7a6f-1234-4e5f-9a0b-1c2d3e4f5a6b")
    private String imageOrderItemId;

    @Schema(description = "촬영항목코드 (공통코드 IMG_ITEM_CD)", example = "02")
    private String imageItemCode;

    @Schema(description = "이 항목의 최종 일정. 아직 없으면 null")
    private ImageScheduleResponseDto schedule;
}
