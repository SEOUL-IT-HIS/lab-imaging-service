package kr.co.seoulit.his.labimagingservice.labspecimen.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import kr.co.seoulit.his.labimagingservice.labspecimen.service.SpecimenService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 검체 식별관리 API
 * 대응 유스케이스: UC-SPC-03 검체식별관리 (Jira ZP2-7)
 *
 * TODO: 엔드포인트 구현 (LabOrderController 참고)
 *   POST   /api/lab-imaging/specimens                검체 채취정보 등록 (ZP2-68)
 *   GET    /api/lab-imaging/specimens/{specimenId}   검체 단건 조회
 *   GET    /api/lab-imaging/specimens                검체 이력 조회 (ZP2-79)
 *
 * ⚠ 응답은 항상 ApiResponse<T> 로 감싸고, 성공 메시지는 LabMessageCode 상수를 쓴다.
 *   검체용 메시지 코드는 아직 없으니 LabMessageCode 에 추가해야 한다 (다음 빈 번호는 LAB018).
 *   추가 시 프론트 messages.ts 도 같이 맞출 것.
 */
@RestController
@RequestMapping("/api/lab-imaging/specimens")
@RequiredArgsConstructor
@Tag(name = "검체 식별관리", description = "UC-SPC-03")
public class SpecimenController {

    private final SpecimenService specimenService;
}
