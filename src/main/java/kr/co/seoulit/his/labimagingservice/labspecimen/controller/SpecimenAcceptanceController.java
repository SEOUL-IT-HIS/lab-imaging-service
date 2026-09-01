package kr.co.seoulit.his.labimagingservice.labspecimen.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.dto.ApiResponse;
import kr.co.seoulit.his.labimagingservice.labspecimen.dto.SpecimenAcceptanceRequestDto;
import kr.co.seoulit.his.labimagingservice.labspecimen.dto.SpecimenAcceptanceSummaryDto;
import kr.co.seoulit.his.labimagingservice.labspecimen.service.SpecimenAcceptanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 검체 인수/적합성 판정 API
 * 대응 유스케이스: UC-SPC-04 검체적합성판정 (Jira ZP2-8)
 *
 * 엔드포인트
 *   POST /api/lab-imaging/specimens/{specimenId}/acceptance   검체 인수 + 적합성 판정
 *
 * ⚠ 인수(ZP2-75)와 판정(ZP2-78)을 따로 두지 않고 한 엔드포인트로 합쳤다.
 *   SPECIMEN_ACCEPTANCE 는 인수정보와 판정결과가 한 행이고 fitness_status_code 가 NOT NULL 이라,
 *   "인수만 되고 판정은 아직" 인 중간 상태를 테이블이 표현하지 못한다.
 *   나눠 두면 인수 시점에 판정값을 임시로 채워 넣어야 하고, 그건 없는 판정을 있는 것처럼 남기는 것이다.
 *
 * ⚠ 검체 하위 경로에 단수형 /acceptance 로 둔다.
 *   판정은 검체 없이 혼자 존재할 수 없고, 검체 1건당 1건뿐이라 복수형이 맞지 않는다.
 *
 * ⚠ 대상 검체는 경로변수로 받는다. 이미 존재하는 리소스를 지목하는 행위라서,
 *   최초 등록(POST /lab-schedules, 대상ID를 바디로)이 아니라
 *   재조정(POST /lab-schedules/{labReceptionId}/reschedule) 쪽 규칙을 따른다.
 *
 * ⚠ 검체를 지목하는 경로는 두 가지다. 둘 다 이 API 로 모인다. (ZP2-75)
 *   1) 워크리스트에서 접수 선택 → 그 접수의 검체 목록에서 클릭
 *   2) 바코드 입력 → GET /api/lab-imaging/specimens/barcode/{specimenBarcode} → 목록에서 자동 선택
 *   2번이 1번을 대체하지 않는다. 입력 수단을 하나 늘린 것이다.
 *
 * ⚠ 2번에서 "지금 선택한 접수의 검체인지" 대조는 서버가 하지 않는다. 화면이 응답의 receptionNo 로 한다.
 *   서버는 화면이 어느 접수를 보고 있는지 모른다. 사유는 SpecimenService.getSpecimenByBarcode 주석 참고.
 */
@RestController
@RequestMapping("/api/lab-imaging/specimens")
@RequiredArgsConstructor
@Tag(name = "검체 적합성판정", description = "UC-SPC-04")
public class SpecimenAcceptanceController {

    private final SpecimenAcceptanceService specimenAcceptanceService;

    @Operation(summary = "검체 인수 및 적합성 판정",
            description = "검체를 인수하면서 적합/부적합을 함께 판정한다. "
                    + "부적합인 경우 부적합사유코드가 필수이며, 재채취 요청 여부를 함께 기록한다.")
    @PostMapping("/{specimenId}/acceptance")
    public ResponseEntity<ApiResponse<SpecimenAcceptanceSummaryDto>> acceptSpecimen(
            @PathVariable String specimenId,
            @Valid @RequestBody SpecimenAcceptanceRequestDto request) {

        SpecimenAcceptanceSummaryDto response = specimenAcceptanceService.acceptSpecimen(specimenId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(response, LabMessageCode.LAB021, "검체 인수 및 적합성 판정이 등록되었습니다.")
        );
    }
}
