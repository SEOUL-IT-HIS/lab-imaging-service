package kr.co.seoulit.his.labimagingservice.labspecimen.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.dto.ApiResponse;
import kr.co.seoulit.his.labimagingservice.laborder.dto.LabOrderSummaryDto;
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
 * TODO: 엔드포인트 구현 (LabOrderController 참고)
 *   POST   /api/lab-imaging/specimens                검체 채취정보 등록 (ZP2-68)
 *   GET    /api/lab-imaging/specimens/{specimenId}   검체 단건 조회
 *   GET    /api/lab-imaging/specimens                미판정 검체 목록 조회 (ZP2-79)
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

    @Operation(summary = "미판정 검체 목록 조회",
            description = "아직 인수/적합성 판정이 등록되지 않은 검체 목록을 조회한다. (적합성판정 대상)")
    @GetMapping
    public ResponseEntity<ApiResponse<List<SpecimenSummaryDto>>> getUnjudgedSpecimens() {
        List<SpecimenSummaryDto> response = specimenService.getUnjudgedSpecimens();
        return ResponseEntity.ok(
                ApiResponse.success(response, LabMessageCode.LAB019, "미판정 검체 목록 조회에 성공했습니다.")
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
