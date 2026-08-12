package kr.co.seoulit.his.labimagingservice.laborder.controller;

import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.dto.ApiResponse;
import kr.co.seoulit.his.labimagingservice.laborder.dto.LabOrderCreateRequestDto;
import kr.co.seoulit.his.labimagingservice.laborder.dto.LabOrderSummaryDto;
import kr.co.seoulit.his.labimagingservice.laborder.dto.LabReceptionDetailDto;
import kr.co.seoulit.his.labimagingservice.laborder.service.LabOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @Operation(summary = "검사 접수 목록 조회",
            description = "검사접수(LAB_RECEPTION) 목록을 조회한다. "
                    + "scheduledYn=N 이면 일정 미등록(일정등록 대상), Y 이면 일정 등록됨(재조정 대상), "
                    + "생략하면 전체를 반환한다.")
    @GetMapping("/receptions")
    public ResponseEntity<ApiResponse<List<LabOrderSummaryDto>>> getReceptions(
            @RequestParam(required = false) String scheduledYn) {
        List<LabOrderSummaryDto> response = labOrderService.getReceptions(scheduledYn);
        return ResponseEntity.ok(
                ApiResponse.success(response, LabMessageCode.LAB003, "검사 접수 목록 조회에 성공했습니다.")
        );
    }

    /**
     * 단건 조회는 접수번호를 경로변수(/receptions/{receptionNo})로 받는다.
     * - 목록("/receptions")과 경로 자체가 달라 매핑 충돌이 없다. (params 분기 불필요)
     * - REST 컨벤션상 "컬렉션(/receptions) vs 개별 리소스(/receptions/{id})" 구분에 부합한다.
     */
    @Operation(summary = "검사 접수 단건 조회", description = "접수번호로 검사접수(LAB_RECEPTION) 정보를 조회한다.")
    @GetMapping("/receptions/{receptionNo}")
    public ResponseEntity<ApiResponse<LabReceptionDetailDto>> getReceptionByNo(
            @PathVariable String receptionNo) {
        LabReceptionDetailDto response = labOrderService.getReceptionByNo(receptionNo);
        return ResponseEntity.ok(
                ApiResponse.success(response, LabMessageCode.LAB003, "검사 접수 단건 조회에 성공했습니다.")
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
