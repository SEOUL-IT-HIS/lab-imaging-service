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
 *   POST   /api/lab-imaging/specimens                검체 채취정보 등록 (ZP2-68)
 *   GET    /api/lab-imaging/specimens?judgedYn=      검체 목록 조회 (판정여부 필터, ZP2-79)
 *   GET    /api/lab-imaging/specimens/{specimenId}   검체 단건 조회
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
    @GetMapping
    public ResponseEntity<ApiResponse<List<SpecimenSummaryDto>>> getSpecimens(
            @RequestParam(required = false) String judgedYn,
            @RequestParam(required = false) String receptionNo) {
        List<SpecimenSummaryDto> response = specimenService.getSpecimens(judgedYn, receptionNo);
        return ResponseEntity.ok(
                ApiResponse.success(response, LabMessageCode.LAB019, "검체 목록 조회에 성공했습니다.")
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
