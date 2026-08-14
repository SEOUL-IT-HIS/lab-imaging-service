package kr.co.seoulit.his.labimagingservice.laborder.controller;

import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.dto.ApiResponse;
import kr.co.seoulit.his.labimagingservice.laborder.dto.LabOrderCreateRequestDto;
import kr.co.seoulit.his.labimagingservice.laborder.dto.LabOrderSummaryDto;
import kr.co.seoulit.his.labimagingservice.laborder.dto.LabReceptionDetailDto;
import kr.co.seoulit.his.labimagingservice.laborder.dto.LabWorklistItemDto;
import kr.co.seoulit.his.labimagingservice.laborder.dto.ReceptionExclusionRequestDto;
import kr.co.seoulit.his.labimagingservice.laborder.service.LabOrderService;
import kr.co.seoulit.his.labimagingservice.laborder.service.LabWorklistService;
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
    private final LabWorklistService labWorklistService;

    @Operation(summary = "검사 워크리스트 조회",
            description = "결과 등록 전까지의 검사 접수를 진행 상태와 함께 조회한다. "
                    + "receptionStatusCode=ACCEPTED 이면 처리 대상, EXCLUDED 이면 제외된 건, "
                    + "생략하면 전체를 반환한다. 오래 대기한 건이 위로 오도록 접수일시 오름차순이다.")
    @GetMapping("/worklist")
    public ResponseEntity<ApiResponse<List<LabWorklistItemDto>>> getWorklist(
            @RequestParam(required = false) String receptionStatusCode) {
        List<LabWorklistItemDto> response = labWorklistService.getWorklist(receptionStatusCode);
        return ResponseEntity.ok(
                ApiResponse.success(response, LabMessageCode.LAB023, "워크리스트 조회에 성공했습니다.")
        );
    }

    /**
     * 제외/복구는 같은 리소스(접수의 "제외 상태")를 만들고 지우는 것이라
     * 하나의 경로에 POST / DELETE 로 짝을 맞춘다.
     * 별도 동사 경로(/exclude, /restore)를 두는 것보다 짝 관계가 드러난다.
     */
    @Operation(summary = "접수 워크리스트 제외",
            description = "처리하지 않기로 판단한 접수를 워크리스트에서 뺀다. 사유는 필수이며, 삭제가 아니라 복구 가능한 상태 변경이다.")
    @PostMapping("/receptions/{receptionNo}/exclusion")
    public ResponseEntity<ApiResponse<Void>> excludeReception(
            @PathVariable String receptionNo,
            @Valid @RequestBody ReceptionExclusionRequestDto request) {

        labOrderService.excludeReception(receptionNo, request.getExclusionReason());

        return ResponseEntity.ok(
                ApiResponse.success(null, LabMessageCode.LAB024, "접수를 워크리스트에서 제외했습니다.")
        );
    }

    @Operation(summary = "접수 워크리스트 복구",
            description = "제외된 접수를 워크리스트로 되돌린다. 제외 상태가 아니면 LAB026 으로 실패한다.")
    @DeleteMapping("/receptions/{receptionNo}/exclusion")
    public ResponseEntity<ApiResponse<Void>> restoreReception(@PathVariable String receptionNo) {

        labOrderService.restoreReception(receptionNo);

        return ResponseEntity.ok(
                ApiResponse.success(null, LabMessageCode.LAB025, "접수를 워크리스트로 복구했습니다.")
        );
    }

    /*
     * 접수 목록 조회(GET /receptions?scheduledYn=)는 삭제했다. (2026-08-14)
     * 워크리스트(GET /worklist)가 같은 목록을 진행 상태까지 얹어서 대체한다.
     * 일정 등록 여부만 거르는 목록은 화면에서 더 이상 쓰지 않는다.
     */

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
