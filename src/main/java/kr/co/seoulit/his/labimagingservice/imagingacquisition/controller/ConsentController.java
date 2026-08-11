package kr.co.seoulit.his.labimagingservice.imagingacquisition.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import kr.co.seoulit.his.labimagingservice.imagingacquisition.service.ConsentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 조영제/침습검사 동의 API
 * 대응 유스케이스: UC-IMG-05 (Jira ZP2-28)
 *
 * TODO: 엔드포인트 구현 (LabOrderController 참고)
 *   POST /api/lab-imaging/consents                        동의 등록 (ZP2-84)
 *   GET  /api/lab-imaging/consents?imageOrderId=          오더별 동의 상태 조회 (ZP2-80)
 *   POST /api/lab-imaging/consents/{consentId}/withdrawal 동의 철회
 *
 * ⚠ 철회를 PATCH 로 할지 POST 하위리소스로 할지는 팀 컨벤션에 맞출 것.
 *   기존 일정 재조정은 POST /{id}/reschedule 형태를 쓰고 있다.
 */
@RestController
@RequestMapping("/api/lab-imaging/consents")
@RequiredArgsConstructor
@Tag(name = "조영제/침습검사 동의", description = "UC-IMG-05")
public class ConsentController {

    private final ConsentService consentService;
}
