package kr.co.seoulit.his.labimagingservice.laborder.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.dto.ApiResponse;
import kr.co.seoulit.his.labimagingservice.laborder.dto.LabItemCatalogDto;
import kr.co.seoulit.his.labimagingservice.laborder.service.LabItemCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 검사항목 카탈로그 검색 API. (처방코어 요청, 2026-10-02)
 *
 * 엔드포인트
 *   GET /api/lab-imaging/lab-items?name=   검사항목을 코드/이름으로 검색 (name 생략 시 전체)
 *
 * ⚠ 처방 생성 화면에서 "검사 항목 고르기" 용도다 — 약제서비스의
 *   GET /api/pharmacy/medications?name= 과 같은 성격이다(처방코어 요청서 참고).
 *   외래/응급/병동 어디서든 쓸 수 있는 공용 조회라 채널(encounterType)을 가리지 않는다.
 */
@RestController
@RequestMapping("/api/lab-imaging/lab-items")
@RequiredArgsConstructor
@Tag(name = "검사항목 카탈로그", description = "처방 화면의 검사항목 검색용 (처방코어 연동)")
public class LabItemCatalogController {

    private final LabItemCatalogService labItemCatalogService;

    @Operation(summary = "검사항목 카탈로그 검색",
            description = "검사항목코드·검사명에 대한 부분일치 검색(대소문자 무관). "
                    + "name 을 생략하면 전체 목록을 반환한다(현재 TEST_TYPE_CD 8건).")
    @GetMapping
    public ResponseEntity<ApiResponse<List<LabItemCatalogDto>>> search(
            @RequestParam(required = false) String name) {
        List<LabItemCatalogDto> response = labItemCatalogService.search(name);
        return ResponseEntity.ok(
                ApiResponse.success(response, LabMessageCode.LAB104, "검사항목 카탈로그 조회에 성공했습니다.")
        );
    }
}
