package kr.co.seoulit.his.labimagingservice.labspecimen.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import kr.co.seoulit.his.labimagingservice.labspecimen.service.SpecimenAcceptanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 검체 인수/적합성 판정 API
 * 대응 유스케이스: UC-SPC-04 검체적합성판정 (Jira ZP2-8)
 *
 * TODO: 엔드포인트 구현
 *   POST /api/lab-imaging/specimen-acceptances            검체 인수 처리 (ZP2-75)
 *   POST /api/lab-imaging/specimen-acceptances/judgments  적합성 판정 (ZP2-78, ZP2-74)
 *
 * ⚠ 경로를 별도 리소스(specimen-acceptances)로 둘지, 검체 하위(/specimens/{id}/acceptance)로
 *   둘지는 인수와 판정을 한 번에 처리할지 여부에 따라 달라진다. 구현 전에 확정할 것.
 */
@RestController
@RequestMapping("/api/lab-imaging/specimen-acceptances")
@RequiredArgsConstructor
@Tag(name = "검체 적합성판정", description = "UC-SPC-04")
public class SpecimenAcceptanceController {

    private final SpecimenAcceptanceService specimenAcceptanceService;
}
