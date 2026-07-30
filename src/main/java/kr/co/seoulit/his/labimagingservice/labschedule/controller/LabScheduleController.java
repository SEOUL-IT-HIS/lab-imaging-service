package kr.co.seoulit.his.labimagingservice.labschedule.controller;

import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.co.seoulit.his.labimagingservice.labschedule.dto.LabScheduleCreateRequestDto;
import kr.co.seoulit.his.labimagingservice.labschedule.dto.LabScheduleRescheduleRequestDto;
import kr.co.seoulit.his.labimagingservice.labschedule.dto.LabScheduleResponseDto;
import kr.co.seoulit.his.labimagingservice.labschedule.service.LabScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 검사 일정 관련 API
 * 대응 유스케이스: UC-SPC-02 검사일정관리 (Jira ZP2-XX)
 */

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/lab-imaging/lab-schedules")
@Tag(name = "Lab Schedule", description = "검사 일정 등록 API")
public class LabScheduleController {

    private final LabScheduleService labScheduleService;

    @Operation(summary = "검사 일정 등록", description = "접수된 검사(LAB_RECEPTION)의 일정을 최초 등록한다.")
    @PostMapping
    public ResponseEntity<ApiResponse<LabScheduleResponseDto>> createLabSchedule(
            @Valid @RequestBody LabScheduleCreateRequestDto request) {

        LabScheduleResponseDto response = labScheduleService.createLabSchedule(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(response, LabMessageCode.LAB009, "검사 일정이 등록되었습니다.")
        );
    }

    @Operation(summary = "검사 일정 재등록", description = "접수된 검사(LAB_RECEPTION)의 일정을 재등록한다.")
    @PostMapping("/{labReceptionId}/reschedule")
    public ResponseEntity<ApiResponse<LabScheduleResponseDto>> createLabReschedule(
            @PathVariable String labReceptionId,
            @Valid @RequestBody LabScheduleRescheduleRequestDto request) {

        LabScheduleResponseDto response = labScheduleService.createLabReschedule(labReceptionId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(response, LabMessageCode.LAB010, "검사 일정이 재등록되었습니다.")
        );
    }
}
