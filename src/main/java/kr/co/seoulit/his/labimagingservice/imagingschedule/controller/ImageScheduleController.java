package kr.co.seoulit.his.labimagingservice.imagingschedule.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.dto.ApiResponse;
import kr.co.seoulit.his.labimagingservice.imagingschedule.dto.ImageScheduleCreateRequestDto;
import kr.co.seoulit.his.labimagingservice.imagingschedule.dto.ImageScheduleItemDto;
import kr.co.seoulit.his.labimagingservice.imagingschedule.dto.ImageScheduleRescheduleRequestDto;
import kr.co.seoulit.his.labimagingservice.imagingschedule.dto.ImageScheduleResponseDto;
import kr.co.seoulit.his.labimagingservice.imagingschedule.service.ImageScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/lab-imaging/image-schedules")
@Tag(name = "Image Schedule", description = "영상 촬영 일정 등록 API")
public class ImageScheduleController {

    private final ImageScheduleService imageScheduleService;

    /**
     * ⚠ 일정이 아니라 "촬영항목"을 기준으로 뽑는다.
     *   일정 테이블에서 시작하면 아직 안 잡힌 항목이 목록에서 빠지는데,
     *   등록 화면이 필요로 하는 건 바로 그 미등록 항목이다.
     */
    @Operation(summary = "접수의 촬영항목 + 일정 목록 조회",
            description = "접수번호로 그 접수의 촬영항목을 모두 조회하고, 각 항목의 최종 일정을 함께 담는다. "
                    + "아직 일정이 없는 항목은 schedule 이 null 이다. 일정 등록 화면이 쓴다.")
    @GetMapping("/receptions/{receptionNo}")
    public ResponseEntity<ApiResponse<List<ImageScheduleItemDto>>> getScheduleItemsByReceptionNo(
            @PathVariable String receptionNo) {

        List<ImageScheduleItemDto> response =
                imageScheduleService.getScheduleItemsByReceptionNo(receptionNo);

        return ResponseEntity.ok(
                ApiResponse.success(response, LabMessageCode.LAB008, "영상촬영 접수 조회에 성공했습니다.")
        );
    }

    @Operation(summary = "영상 촬영 일정 등록", description = "접수된 영상검사(IMAGE_RECEPTION)의 일정을 최초 등록한다.")
    @PostMapping
    public ResponseEntity<ApiResponse<ImageScheduleResponseDto>> createImageSchedule(
            @Valid @RequestBody ImageScheduleCreateRequestDto request) {

        ImageScheduleResponseDto response = imageScheduleService.createImageSchedule(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(response, LabMessageCode.LAB011, "영상 일정이 등록되었습니다.")
        );
    }

    @Operation(summary = "영상 촬영 일정 재등록", description = "접수된 영상검사(IMAGE_RECEPTION)의 일정을 재등록한다.")
    @PostMapping("/{imageReceptionId}/reschedule")
    public ResponseEntity<ApiResponse<ImageScheduleResponseDto>> createImageReschedule(
            @PathVariable String imageReceptionId,
            @Valid @RequestBody ImageScheduleRescheduleRequestDto request) {

        ImageScheduleResponseDto response = imageScheduleService.createImageReschedule(imageReceptionId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(response, LabMessageCode.LAB012, "영상 일정이 재등록되었습니다.")
        );
    }

}
