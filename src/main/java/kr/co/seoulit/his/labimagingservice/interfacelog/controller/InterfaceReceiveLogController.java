package kr.co.seoulit.his.labimagingservice.interfacelog.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.dto.ApiResponse;
import kr.co.seoulit.his.labimagingservice.interfacelog.dto.InterfaceReceiveLogSummaryDto;
import kr.co.seoulit.his.labimagingservice.interfacelog.mapper.InterfaceReceiveLogMapper;
import kr.co.seoulit.his.labimagingservice.interfacelog.service.InterfaceReceiveLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 연계 수신 이력 조회 API
 *
 * ⚠ Kafka 는 백엔드끼리의 통신이라 화면에 아무 흔적도 남지 않는다.
 *   워크리스트에 오더가 떠도 REST 로 온 건지 Kafka 로 온 건지 구분할 수 없어서,
 *   "방금 Kafka 로 들어왔습니다"를 보여줄 창구가 필요하다. 그게 이 API 다.
 *   응답의 eventId 가 채워진 행이 Kafka 수신 건이고, rawMessage 에 코어가 보낸 원문이 그대로 있다.
 *
 * ⚠ 내부 조회 API 라 공통 ApiResponse<T> 로 감싼다.
 *   연계 수신 결과(LabOrderIntakeResultDto)만 코어 계약 때문에 감싸지 않는 예외였다.
 *
 * TODO(후속): 기간·출처·결과코드 검색과 페이지네이션. 지금은 최신 20건 고정이다.
 */
@RestController
@RequestMapping("/api/lab-imaging/interface-logs")
@RequiredArgsConstructor
@Tag(name = "연계 수신 이력", description = "Kafka/REST 수신 기록 조회 (운영·시연 확인용)")
public class InterfaceReceiveLogController {

    private final InterfaceReceiveLogService interfaceReceiveLogService;
    private final InterfaceReceiveLogMapper interfaceReceiveLogMapper;

    @Operation(summary = "연계 수신 이력 조회",
            description = "최근 20건을 수신일시 내림차순으로 조회한다. "
                    + "eventId 가 있으면 Kafka 수신, 없으면 REST 수신 건이다.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<InterfaceReceiveLogSummaryDto>>> getRecent() {
        List<InterfaceReceiveLogSummaryDto> response =
                interfaceReceiveLogMapper.toResponseList(interfaceReceiveLogService.findRecent());

        return ResponseEntity.ok(
                ApiResponse.success(response, LabMessageCode.LAB032, "연계 수신 이력 조회에 성공했습니다.")
        );
    }
}
