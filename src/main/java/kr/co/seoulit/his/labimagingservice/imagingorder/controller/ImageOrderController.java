package kr.co.seoulit.his.labimagingservice.imagingorder.controller;

import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.dto.ApiResponse;
import kr.co.seoulit.his.labimagingservice.imagingorder.dto.ImageOrderCreateRequestDto;
import kr.co.seoulit.his.labimagingservice.imagingorder.dto.ImageOrderSummaryDto;
import kr.co.seoulit.his.labimagingservice.imagingorder.dto.ImageReceptionDetailDto;
import kr.co.seoulit.his.labimagingservice.imagingorder.dto.ImageWorklistItemDto;
import kr.co.seoulit.his.labimagingservice.laborder.dto.ReceptionExclusionRequestDto;
import kr.co.seoulit.his.labimagingservice.imagingorder.service.ImageOrderService;
import kr.co.seoulit.his.labimagingservice.imagingorder.service.ImageWorklistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 영상 오더 관련 API
 * 대응 유스케이스: UC-IMG-01 영상오더접수 (Jira ZP2-19)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/lab-imaging/image-orders")
@Tag(name = "Image Order", description = "영상 오더 접수 API")
public class ImageOrderController {

    private final ImageOrderService imageOrderService;
    private final ImageWorklistService imageWorklistService;

    /**
     * ⚠ 기존 GET /receptions 와 목적이 다르다. 지우거나 합치지 않는다.
     *   /receptions 는 "일정 등록 대상 고르기"용이라 scheduledYn 으로 거르고 최신순이다.
     *   /worklist 는 "오늘 처리할 접수 전부"라 제외 여부로 거르고 오래된 건이 위다.
     *   (검사 쪽도 두 API 를 함께 두고 있다)
     */
    @Operation(summary = "영상 워크리스트 조회",
            description = "영상 업무 화면의 접수 목록을 진행 상태(일정·동의)와 함께 조회한다. "
                    + "receptionStatusCode=ACCEPTED 면 처리 대상, EXCLUDED 면 제외된 건, "
                    + "생략하면 전체를 반환한다. 오래 대기한 접수가 위로 온다.")
    @GetMapping("/worklist")
    public ResponseEntity<ApiResponse<List<ImageWorklistItemDto>>> getWorklist(
            @RequestParam(required = false) String receptionStatusCode) {

        List<ImageWorklistItemDto> response = imageWorklistService.getWorklist(receptionStatusCode);

        return ResponseEntity.ok(
                ApiResponse.success(response, LabMessageCode.LAB042, "영상 워크리스트 조회에 성공했습니다.")
        );
    }

    @Operation(summary = "영상 접수 워크리스트 제외",
            description = "접수를 워크리스트에서 뺀다. 삭제가 아니라 복구 가능한 상태 변경이며 사유가 필수다.")
    @PostMapping("/receptions/{receptionNo}/exclusion")
    public ResponseEntity<ApiResponse<Void>> excludeReception(
            @PathVariable String receptionNo,
            @Valid @RequestBody ReceptionExclusionRequestDto request) {

        imageOrderService.excludeReception(receptionNo, request.getExclusionReason());

        return ResponseEntity.ok(
                ApiResponse.success(null, LabMessageCode.LAB043, "영상 접수가 워크리스트에서 제외되었습니다.")
        );
    }

    @Operation(summary = "영상 접수 워크리스트 복구",
            description = "제외된 접수를 워크리스트로 되돌린다. 제외 상태가 아니면 LAB045 로 거절한다.")
    @PostMapping("/receptions/{receptionNo}/restoration")
    public ResponseEntity<ApiResponse<Void>> restoreReception(@PathVariable String receptionNo) {

        imageOrderService.restoreReception(receptionNo);

        return ResponseEntity.ok(
                ApiResponse.success(null, LabMessageCode.LAB044, "영상 접수가 워크리스트로 복구되었습니다.")
        );
    }

    @Operation(summary = "영상 촬영 접수 목록 조회",
            description = "영상접수(IMAGE_RECEPTION) 목록을 조회한다. "
                    + "scheduledYn=N 이면 일정 미등록(일정등록 대상), Y 이면 일정 등록됨(재조정 대상), "
                    + "생략하면 전체를 반환한다.")
    @GetMapping("/receptions")
    public ResponseEntity<ApiResponse<List<ImageOrderSummaryDto>>> getReceptions(
            @RequestParam(required = false) String scheduledYn) {
        List<ImageOrderSummaryDto> response = imageOrderService.getReceptions(scheduledYn);
        return ResponseEntity.ok(
                ApiResponse.success(response, LabMessageCode.LAB003, "영상 촬영 접수 목록 조회에 성공했습니다.")
        );
    }

    /**
     * 단건 조회는 접수번호를 경로변수(/receptions/{receptionNo})로 받는다.
     * - 목록("/receptions")과 경로 자체가 달라 매핑 충돌이 없다. (params 분기 불필요)
     * - REST 컨벤션상 "컬렉션(/receptions) vs 개별 리소스(/receptions/{id})" 구분에 부합한다.
     */
    @Operation(summary = "영상 촬영 접수 단건 조회", description = "접수번호로 영상접수(IMAGE_RECEPTION) 정보를 조회한다.")
    @GetMapping("/receptions/{receptionNo}")
    public ResponseEntity<ApiResponse<ImageReceptionDetailDto>> getReceptionByNo(
            @PathVariable String receptionNo) {
        ImageReceptionDetailDto response = imageOrderService.getReceptionByNo(receptionNo);
        return ResponseEntity.ok(
                ApiResponse.success(response, LabMessageCode.LAB003, "검사 접수 단건 조회에 성공했습니다.")
        );
    }

    @Operation(summary = "영상 오더 접수", description = "외부 시스템에서 발생한 영상 오더를 접수하고, 영상검사접수(IMAGE_RECEPTION)를 함께 생성한다. "
            + "(2026-07-16 기준: 외래/병동/응급이 직접 호출하지 않고 GR2 처방코어(/api/orders)가 "
            + "라우팅하여 호출하는 구조로 변경됨 — Q-ROUTE-OWNER/Q-EXAM 확정 전까지는 참고용)")
    @PostMapping
    public ResponseEntity<ApiResponse<ImageOrderSummaryDto>> createOrder(
            @Valid @RequestBody ImageOrderCreateRequestDto request) {

        ImageOrderSummaryDto response = imageOrderService.createOrder(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(response, LabMessageCode.LAB005, "영상 접수가 생성되었습니다.")
        );
    }
}
