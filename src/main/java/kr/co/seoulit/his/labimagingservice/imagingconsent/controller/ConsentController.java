package kr.co.seoulit.his.labimagingservice.imagingconsent.controller;

import kr.co.seoulit.his.common.session.SessionUser;
import kr.co.seoulit.his.labimagingservice.common.session.ActorIdResolver;
import kr.co.seoulit.his.labimagingservice.common.session.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.dto.ApiResponse;
import kr.co.seoulit.his.labimagingservice.imagingconsent.dto.ConsentCreateRequestDto;
import kr.co.seoulit.his.labimagingservice.imagingconsent.dto.ConsentSummaryDto;
import kr.co.seoulit.his.labimagingservice.imagingconsent.dto.ConsentWithdrawRequestDto;
import kr.co.seoulit.his.labimagingservice.imagingconsent.service.ConsentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 조영제/침습검사 동의 API
 * 대응 유스케이스: UC-IMG-05 (Jira ZP2-28)
 *
 * 엔드포인트
 *   POST /api/lab-imaging/consents                   동의 등록 (ZP2-84, ZP2-83)
 *   GET  /api/lab-imaging/consents?imageOrderId=     오더별 동의 이력 조회 (ZP2-80)
 *   POST /api/lab-imaging/consents/{consentId}/withdrawal   동의 철회 (5차 Phase 9-3)
 *
 * ⚠ 철회 경로는 POST /{consentId}/withdrawal — 일정 재조정이 POST /{labReceptionId}/reschedule 형태를
 *   쓰고 있어 그 규칙에 맞췄다. (1차 배포 때 4차로 이월했던 기능을 5차에 붙였다)
 *
 * ⚠ 대상 오더를 조회는 쿼리파라미터로, 등록은 바디로 받는다.
 *   등록은 신규 리소스를 만드는 것이라 오더ID가 요청 데이터의 일부이고(LabScheduleController.create 와 동일),
 *   조회는 목록의 필터 조건이라 쿼리파라미터가 맞다.
 */
@RestController
@RequestMapping("/api/lab-imaging/consents")
@RequiredArgsConstructor
@Tag(name = "조영제/침습검사 동의", description = "UC-IMG-05")
public class ConsentController {

    private final ConsentService consentService;
    private final ActorIdResolver actorIdResolver;

    @Operation(summary = "동의 등록",
            description = "영상오더에 대한 조영제/침습검사 동의를 등록한다. "
                    + "같은 오더에 같은 유형의 철회되지 않은 동의가 이미 있으면 실패한다.")
    @PostMapping
    public ResponseEntity<ApiResponse<ConsentSummaryDto>> createConsent(
            @LoginUser SessionUser loginUser,
            @Valid @RequestBody ConsentCreateRequestDto request) {

        ConsentSummaryDto response = consentService.createConsent(request.toBuilder()
                        .witnessId(actorIdResolver.resolve(loginUser, request.getWitnessId(), "witnessId")).build());

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(response, LabMessageCode.LAB028, "동의 정보가 등록되었습니다.")
        );
    }

    @Operation(summary = "동의 철회",
            description = "동의(Y)를 철회한다. 철회사유코드(CONSENT_WITHDRAW_CD) 필수. 철회일시는 서버 시각, 처리자는 로그인 사용자. "
                    + "이미 철회된 건 LAB095, 거부 기록 LAB096. 이미 촬영된 항목이 있어도 철회되며(D14) "
                    + "그때는 LAB097 '이미 촬영된 영상은 그대로 유지됩니다'로 응답한다.")
    @PostMapping("/{consentId}/withdrawal")
    public ResponseEntity<ApiResponse<ConsentSummaryDto>> withdrawConsent(
            @LoginUser SessionUser loginUser,
            @PathVariable String consentId,
            @Valid @RequestBody ConsentWithdrawRequestDto request) {

        ConsentService.WithdrawResult result = consentService.withdrawConsent(consentId, request.toBuilder()
                .withdrawnById(actorIdResolver.resolve(loginUser, request.getWithdrawnById(), "withdrawnById")).build());

        return ResponseEntity.ok(result.acquired()
                ? ApiResponse.success(result.consent(), LabMessageCode.LAB097,
                        "동의가 철회되었습니다. 이미 촬영된 영상은 그대로 유지됩니다.")
                : ApiResponse.success(result.consent(), LabMessageCode.LAB093, "동의가 철회되었습니다."));
    }

    @Operation(summary = "오더별 동의 이력 조회",
            description = "영상오더 1건의 동의 이력을 최신순으로 조회한다. 철회된 건도 함께 내려간다. "
                    + "동의를 받지 않은 오더는 빈 배열이 반환된다.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<ConsentSummaryDto>>> getConsents(
            @RequestParam String imageOrderId) {

        List<ConsentSummaryDto> response = consentService.getConsentsByImageOrderId(imageOrderId);

        return ResponseEntity.ok(
                ApiResponse.success(response, LabMessageCode.LAB029, "동의 정보 조회에 성공했습니다.")
        );
    }
}
