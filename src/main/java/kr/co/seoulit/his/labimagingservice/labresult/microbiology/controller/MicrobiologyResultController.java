package kr.co.seoulit.his.labimagingservice.labresult.microbiology.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.co.seoulit.his.common.session.SessionUser;
import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.dto.ApiResponse;
import kr.co.seoulit.his.labimagingservice.common.session.ActorIdResolver;
import kr.co.seoulit.his.labimagingservice.common.session.LoginUser;
import kr.co.seoulit.his.labimagingservice.labresult.dto.LabResultConfirmRequestDto;
import kr.co.seoulit.his.labimagingservice.labresult.microbiology.dto.MicrobiologyResultCreateRequestDto;
import kr.co.seoulit.his.labimagingservice.labresult.microbiology.dto.MicrobiologyResultSummaryDto;
import kr.co.seoulit.his.labimagingservice.labresult.microbiology.dto.MicrobiologyResultUpdateRequestDto;
import kr.co.seoulit.his.labimagingservice.labresult.microbiology.service.MicrobiologyResultService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 미생물검사결과 API
 * 대응 유스케이스: UC-RST-02 미생물검사결과등록 (Jira ZP2-14: 87~92)
 *
 * 엔드포인트
 *   POST /api/lab-imaging/microbiology-results                          등록 (중간보고, 01)
 *   PUT  /api/lab-imaging/microbiology-results/{id}                     수정 (확정 전만)
 *   POST /api/lab-imaging/microbiology-results/{id}/confirm             확정 (최종보고, 02)
 *   GET  /api/lab-imaging/microbiology-results/{id}                     단건 (감수성 포함)
 *   GET  /api/lab-imaging/microbiology-results/receptions/{receptionNo} 접수의 결과 목록
 *
 * ⚠ 확정 요청 본문은 일반검사와 같은 LabResultConfirmRequestDto 를 쓴다(확정자ID 하나뿐이라 모양이 같다).
 */
@RestController
@RequestMapping("/api/lab-imaging/microbiology-results")
@RequiredArgsConstructor
@Tag(name = "미생물검사 결과", description = "UC-RST-02")
public class MicrobiologyResultController {

    private final MicrobiologyResultService microbiologyResultService;
    private final ActorIdResolver actorIdResolver;

    @Operation(summary = "미생물 결과 등록",
            description = "적합 판정된 검체에 배양상태·균종·감수성을 등록한다(결과상태 01=중간보고). "
                    + "접수의 미생물 항목이 정확히 1건이어야 하고(LAB074), 접수당 결과는 1건이다(LAB075).")
    @PostMapping
    public ResponseEntity<ApiResponse<MicrobiologyResultSummaryDto>> createResult(
            @LoginUser SessionUser loginUser,
            @Valid @RequestBody MicrobiologyResultCreateRequestDto request) {

        MicrobiologyResultSummaryDto response = microbiologyResultService.createResult(request.toBuilder()
                .recordedById(actorIdResolver.resolve(loginUser, request.getRecordedById(), "recordedById")).build());

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(response, LabMessageCode.LAB069, "미생물 검사 결과가 등록되었습니다."));
    }

    @Operation(summary = "미생물 결과 수정", description = "확정 전(01)만 수정할 수 있다. 감수성 목록은 통째로 교체한다.")
    @PutMapping("/{microbiologyResultId}")
    public ResponseEntity<ApiResponse<MicrobiologyResultSummaryDto>> updateResult(
            @PathVariable String microbiologyResultId,
            @Valid @RequestBody MicrobiologyResultUpdateRequestDto request) {

        MicrobiologyResultSummaryDto response = microbiologyResultService.updateResult(microbiologyResultId, request);

        return ResponseEntity.ok(
                ApiResponse.success(response, LabMessageCode.LAB072, "미생물 검사 결과가 수정되었습니다."));
    }

    @Operation(summary = "미생물 결과 확정", description = "01 → 02(최종보고). 확정 후 수정 불가. 확정자는 로그인 사용자.")
    @PostMapping("/{microbiologyResultId}/confirm")
    public ResponseEntity<ApiResponse<MicrobiologyResultSummaryDto>> confirmResult(
            @PathVariable String microbiologyResultId,
            @LoginUser SessionUser loginUser,
            @Valid @RequestBody LabResultConfirmRequestDto request) {

        String confirmedById = actorIdResolver.resolve(loginUser, request.getConfirmedById(), "confirmedById");
        MicrobiologyResultSummaryDto response = microbiologyResultService.confirmResult(microbiologyResultId, confirmedById);

        return ResponseEntity.ok(
                ApiResponse.success(response, LabMessageCode.LAB073, "미생물 검사 결과가 확정되었습니다."));
    }

    @Operation(summary = "미생물 결과 단건 조회", description = "감수성 목록 포함")
    @GetMapping("/{microbiologyResultId}")
    public ResponseEntity<ApiResponse<MicrobiologyResultSummaryDto>> getResult(@PathVariable String microbiologyResultId) {
        return ResponseEntity.ok(ApiResponse.success(
                microbiologyResultService.getResult(microbiologyResultId),
                LabMessageCode.LAB070, "미생물 검사 결과 조회에 성공했습니다."));
    }

    @Operation(summary = "접수의 미생물 결과 목록", description = "접수당 1건 제약이라 보통 0~1건. 0건은 빈 목록.")
    @GetMapping("/receptions/{receptionNo}")
    public ResponseEntity<ApiResponse<List<MicrobiologyResultSummaryDto>>> getResultsByReception(
            @PathVariable String receptionNo) {
        return ResponseEntity.ok(ApiResponse.success(
                microbiologyResultService.getResultsByReceptionNo(receptionNo),
                LabMessageCode.LAB070, "미생물 검사 결과 조회에 성공했습니다."));
    }
}
