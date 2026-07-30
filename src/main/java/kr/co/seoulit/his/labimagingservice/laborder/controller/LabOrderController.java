package kr.co.seoulit.his.labimagingservice.laborder.controller;

import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.dto.ApiResponse;
import kr.co.seoulit.his.labimagingservice.laborder.dto.LabOrderCreateRequestDto;
import kr.co.seoulit.his.labimagingservice.laborder.dto.LabOrderSummaryDto;
import kr.co.seoulit.his.labimagingservice.laborder.service.LabOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 검사 오더 관련 API
 * 대응 유스케이스: UC-SPC-01 검사오더접수 (Jira ZP2-12)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/lab-imaging/lab-orders")
@Tag(name = "Lab Order", description = "검사 오더 접수 API")
public class LabOrderController {

    private final LabOrderService labOrderService;

    @Operation(summary = "검사 접수 단건 조회", description = "접수번호로 검사접수(LAB_RECEPTION) 정보를 조회한다.")
    @GetMapping("/receptions")
    public ResponseEntity<ApiResponse<LabOrderSummaryDto>> getReceptionByNo(
            @RequestParam String receptionNo) {
        LabOrderSummaryDto response = labOrderService.getReceptionByNo(receptionNo);
        return ResponseEntity.ok(
                ApiResponse.success(response, LabMessageCode.LAB003, "접수 조회에 성공했습니다.")
        );
    }

    @Operation(summary = "검사 오더 접수", description = "외부 시스템에서 발생한 검사 오더를 접수하고, 검사접수(LAB_RECEPTION)를 함께 생성한다. "
            + "(2026-07-16 기준: 외래/병동/응급이 직접 호출하지 않고 GR2 처방코어(/api/orders)가 "
            + "라우팅하여 호출하는 구조로 변경됨 — Q-ROUTE-OWNER/Q-EXAM 확정 전까지는 참고용)")
    @PostMapping
    public ResponseEntity<ApiResponse<LabOrderSummaryDto>> createOrder(
            @Valid @RequestBody LabOrderCreateRequestDto request) {

        LabOrderSummaryDto response = labOrderService.createOrder(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(response, LabMessageCode.LAB001, "검사 접수가 생성되었습니다.")
        );
    }
}
