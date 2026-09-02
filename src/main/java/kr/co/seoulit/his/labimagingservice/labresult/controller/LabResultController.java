package kr.co.seoulit.his.labimagingservice.labresult.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.dto.ApiResponse;
import kr.co.seoulit.his.labimagingservice.labresult.dto.LabResultConfirmRequestDto;
import kr.co.seoulit.his.labimagingservice.labresult.dto.LabResultCreateRequestDto;
import kr.co.seoulit.his.labimagingservice.labresult.dto.LabResultSummaryDto;
import kr.co.seoulit.his.labimagingservice.labresult.dto.LabResultUpdateRequestDto;
import kr.co.seoulit.his.labimagingservice.labresult.service.LabResultService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 일반검사 결과 API
 * 대응 유스케이스: UC-RST-01 일반검사결과등록 (Jira ZP2-13)
 *
 * 엔드포인트
 *   POST   /api/lab-imaging/lab-results                        결과 등록 (ZP2-100)
 *   PUT    /api/lab-imaging/lab-results/{labResultId}          결과 수정 (확정 전만)
 *   POST   /api/lab-imaging/lab-results/{labResultId}/confirm  결과 확정 (ZP2-101)
 *   GET    /api/lab-imaging/lab-results/{labResultId}          단건 조회
 *   GET    /api/lab-imaging/lab-results?labOrderItemId=        검사항목으로 조회
 *
 * ⚠ 확정은 PUT 이 아니라 POST /{id}/confirm 이다.
 *   PUT 은 "이 값으로 바꿔라"이고, 확정은 값 교체가 아니라 상태를 한 방향으로 넘기는 행위다.
 *   일정 재조정(POST /lab-schedules/{id}/reschedule)과 같은 규칙을 따랐다.
 *
 * ⚠ 응답은 항상 ApiResponse<T> 로 감싸고, 성공 메시지는 LabMessageCode 상수를 쓴다.
 *
 * ⚠ 장비 연동으로 결과를 받는 엔드포인트는 없다. 수기 입력만 있다. (2026-08-31 범위 결정)
 */
@RestController
@RequestMapping("/api/lab-imaging/lab-results")
@RequiredArgsConstructor
@Tag(name = "일반검사 결과", description = "UC-RST-01")
public class LabResultController {

    private final LabResultService labResultService;

    @Operation(summary = "일반검사 결과 등록",
            description = "검사항목(LAB_ORDER_ITEM) 1건에 대한 결과를 수기로 등록한다. "
                    + "검사항목 1건에 결과 1건이라 이미 등록된 항목이면 LAB036 으로 거절한다. "
                    + "비정상 여부(abnormalYn)와 결과상태(01=등록)는 요청값이 아니라 서버가 정한다.")
    @PostMapping
    public ResponseEntity<ApiResponse<LabResultSummaryDto>> createLabResult(
            @Valid @RequestBody LabResultCreateRequestDto request) {

        LabResultSummaryDto response = labResultService.createLabResult(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(response, LabMessageCode.LAB033, "검사 결과가 등록되었습니다.")
        );
    }

    @Operation(summary = "일반검사 결과 수정",
            description = "확정 전(결과상태 01)인 결과만 수정할 수 있다. "
                    + "이미 확정된 결과를 수정하려 하면 LAB040 으로 거절한다. "
                    + "참고범위가 바뀌면 비정상 여부를 서버가 다시 계산한다.")
    @PutMapping("/{labResultId}")
    public ResponseEntity<ApiResponse<LabResultSummaryDto>> updateLabResult(
            @PathVariable String labResultId,
            @Valid @RequestBody LabResultUpdateRequestDto request) {

        LabResultSummaryDto response = labResultService.updateLabResult(labResultId, request);

        return ResponseEntity.ok(
                ApiResponse.success(response, LabMessageCode.LAB038, "검사 결과가 수정되었습니다.")
        );
    }

    @Operation(summary = "일반검사 결과 확정",
            description = "결과상태를 01(등록)에서 02(확정)로 전이하고 확정일시·확정자를 기록한다. "
                    + "확정 후에는 수정할 수 없다. 이미 확정된 건은 LAB041 로 거절한다.")
    @PostMapping("/{labResultId}/confirm")
    public ResponseEntity<ApiResponse<LabResultSummaryDto>> confirmLabResult(
            @PathVariable String labResultId,
            @Valid @RequestBody LabResultConfirmRequestDto request) {

        LabResultSummaryDto response =
                labResultService.confirmLabResult(labResultId, request.getConfirmedById());

        return ResponseEntity.ok(
                ApiResponse.success(response, LabMessageCode.LAB039, "검사 결과가 확정되었습니다.")
        );
    }

    @Operation(summary = "일반검사 결과 단건 조회", description = "검사결과ID로 결과를 조회한다.")
    @GetMapping("/{labResultId}")
    public ResponseEntity<ApiResponse<LabResultSummaryDto>> getLabResultById(
            @PathVariable String labResultId) {

        LabResultSummaryDto response = labResultService.getLabResultById(labResultId);

        return ResponseEntity.ok(
                ApiResponse.success(response, LabMessageCode.LAB034, "검사 결과 조회에 성공했습니다.")
        );
    }

    /**
     * ⚠ 목록이 아니라 단건을 돌려준다. 검사항목 1건에 결과가 1건뿐이기 때문이다.
     *   결과가 없으면 빈 배열이 아니라 LAB037 로 응답한다 — 화면이 "결과 있음/없음"을 물어보는 API 다.
     */
    @Operation(summary = "검사항목으로 결과 조회",
            description = "검사항목ID(LAB_ORDER_ITEM)로 결과를 조회한다. "
                    + "검사항목 1건에 결과 1건이라 단건으로 응답하며, 없으면 LAB037 로 응답한다.")
    @GetMapping
    public ResponseEntity<ApiResponse<LabResultSummaryDto>> getLabResultByLabOrderItemId(
            @RequestParam String labOrderItemId) {

        LabResultSummaryDto response = labResultService.getLabResultByLabOrderItemId(labOrderItemId);

        return ResponseEntity.ok(
                ApiResponse.success(response, LabMessageCode.LAB034, "검사 결과 조회에 성공했습니다.")
        );
    }
}
