package kr.co.seoulit.his.labimagingservice.laborder.controller;

import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.dto.ApiResponse;
import kr.co.seoulit.his.labimagingservice.laborder.dto.LabOrderCreateRequestDto;
import kr.co.seoulit.his.labimagingservice.laborder.dto.LabOrderCreateResponseDto;
import kr.co.seoulit.his.labimagingservice.laborder.service.LabOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 검사 오더 관련 API
 * 대응 유스케이스: UC-SPC-01 검사오더접수 (Jira ZP2-12)
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Lab Order", description = "검사 오더 접수 API")
public class LabOrderController {

    private final LabOrderService labOrderService;

    @Operation(summary = "검사 오더 접수", description = "외부 시스템에서 발생한 검사 오더를 접수하고, 검사접수(LAB_RECEPTION)를 함께 생성한다. "
            + "(2026-07-16 기준: 외래/병동/응급이 직접 호출하지 않고 GR2 처방코어(/api/orders)가 "
            + "라우팅하여 호출하는 구조로 변경됨 — Q-ROUTE-OWNER/Q-EXAM 확정 전까지는 참고용)")
    @PostMapping("/lab-orders")
    public ResponseEntity<ApiResponse<LabOrderCreateResponseDto>> createOrder(
            @Valid @RequestBody LabOrderCreateRequestDto request) {

        LabOrderCreateResponseDto response = labOrderService.createOrder(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(response, LabMessageCode.LAB001, "검사 접수가 생성되었습니다.")
        );
    }
}
