package kr.co.seoulit.his.labimagingservice.imagingschedule.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.dto.ApiResponse;
import kr.co.seoulit.his.labimagingservice.imagingschedule.dto.ImageScheduleCreateRequestDto;
import kr.co.seoulit.his.labimagingservice.imagingschedule.dto.ImageScheduleRescheduleRequestDto;
import kr.co.seoulit.his.labimagingservice.imagingschedule.dto.ImageScheduleResponseDto;
import kr.co.seoulit.his.labimagingservice.imagingschedule.service.ImageScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/lab-imaging/image-schedules")
@Tag(name = "Image Schedule", description = "영상 촬영 일정 등록 API")
public class ImageScheduleController {

    private final ImageScheduleService imageScheduleService;

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
