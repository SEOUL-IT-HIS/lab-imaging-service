package kr.co.seoulit.his.labimagingservice.labspecimen.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.dto.ApiResponse;
import kr.co.seoulit.his.labimagingservice.labspecimen.dto.SpecimenCreateRequestDto;
import kr.co.seoulit.his.labimagingservice.labspecimen.dto.SpecimenSummaryDto;
import kr.co.seoulit.his.labimagingservice.labspecimen.service.SpecimenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 검체 식별관리 API
 * 대응 유스케이스: UC-SPC-03 검체식별관리 (Jira ZP2-7)
 *
 * 엔드포인트
 *   POST   /api/lab-imaging/specimens                       검체 채취정보 등록 (ZP2-68)
 *   GET    /api/lab-imaging/specimens?judgedYn=             검체 목록 조회 (판정여부 필터, ZP2-79)
 *   GET    /api/lab-imaging/specimens/barcode/{바코드}      검체 바코드 단건 조회 (ZP2-75)
 *   GET    /api/lab-imaging/specimens/{specimenId}          검체 단건 조회
 *
 * ⚠ 응답은 항상 ApiResponse<T> 로 감싸고, 성공 메시지는 LabMessageCode 상수를 쓴다.
 *
 */
@RestController
@RequestMapping("/api/lab-imaging/specimens")
@RequiredArgsConstructor
@Tag(name = "검체 식별관리", description = "UC-SPC-03")
public class SpecimenController {

    private final SpecimenService specimenService;

    @PostMapping
    public ResponseEntity<ApiResponse<SpecimenSummaryDto>> createSpecimen(
            @Valid @RequestBody SpecimenCreateRequestDto request) {

        SpecimenSummaryDto response = specimenService.createSpecimen(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(response, LabMessageCode.LAB018, "검체 정보가 등록되었습니다.")
        );
    }

    @Operation(summary = "검체 목록 조회",
            description = "검체 목록을 조회한다. "
                    + "judgedYn=N 이면 미판정(적합성판정 대상), Y 이면 판정 완료, 생략하면 전체를 반환한다. "
                    + "receptionNo 를 주면 그 접수의 검체만 반환하며 judgedYn 은 무시된다.")
    // TODO(ZP2-79, 후반 작업): 현재 화면은 receptionNo 만 보낸다. judgedYn 은 검체 이력 조회 화면용으로 남겨둔 것이다.
    @GetMapping
    public ResponseEntity<ApiResponse<List<SpecimenSummaryDto>>> getSpecimens(
            @RequestParam(required = false) String judgedYn,
            @RequestParam(required = false) String receptionNo) {
        List<SpecimenSummaryDto> response = specimenService.getSpecimens(judgedYn, receptionNo);
        return ResponseEntity.ok(
                ApiResponse.success(response, LabMessageCode.LAB019, "검체 목록 조회에 성공했습니다.")
        );
    }

    /**
     * ⚠ 아래 /{specimenId} 보다 위에 둔다.
     *   /barcode/{...} 는 앞에 고정 문자열 세그먼트가 있어 Spring 이 더 구체적인 패턴으로 먼저 고르므로
     *   순서를 바꿔도 동작은 같다. 읽는 사람이 "바코드가 specimenId 로 잡히는 것 아닌가" 하고
     *   멈추지 않도록 구체적인 쪽을 위에 두는 것이다.
     */
    @Operation(summary = "검체 바코드 단건 조회",
            description = "검체바코드로 검체를 조회한다. 검사실에 도착한 검체의 바코드를 입력해 "
                    + "판정 대상 검체를 지목할 때 쓴다. 해당 바코드가 없으면 LAB020 으로 응답한다. "
                    + "지금 선택한 접수의 검체인지 여부는 서버가 판단하지 않으며, 응답의 receptionNo 로 화면이 대조한다.")
    @GetMapping("/barcode/{specimenBarcode}")
    public ResponseEntity<ApiResponse<SpecimenSummaryDto>> getSpecimenByBarcode(
            @PathVariable String specimenBarcode) {
        SpecimenSummaryDto response = specimenService.getSpecimenByBarcode(specimenBarcode);
        return ResponseEntity.ok(
                ApiResponse.success(response, LabMessageCode.LAB019, "검체 정보 단건 조회에 성공했습니다.")
        );
    }

    @Operation(summary = "검체 단건 조회", description = "검체ID로 단건의 검체 정보를 조회한다.")
    @GetMapping("/{specimenId}")
    public ResponseEntity<ApiResponse<SpecimenSummaryDto>> getSpecimenById(
            @PathVariable String specimenId) {
        SpecimenSummaryDto response = specimenService.getSpecimenById(specimenId);
        return ResponseEntity.ok(
                ApiResponse.success(response, LabMessageCode.LAB019, "검체 정보 단건 조회에 성공했습니다.")
        );
    }
}
