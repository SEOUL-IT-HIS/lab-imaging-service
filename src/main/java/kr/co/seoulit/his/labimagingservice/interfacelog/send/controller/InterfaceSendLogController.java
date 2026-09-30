package kr.co.seoulit.his.labimagingservice.interfacelog.send.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.dto.ApiResponse;
import kr.co.seoulit.his.labimagingservice.common.dto.PageResponse;
import kr.co.seoulit.his.labimagingservice.interfacelog.send.dto.InterfaceSendLogDto;
import kr.co.seoulit.his.labimagingservice.interfacelog.send.service.InterfaceSendLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * 발신 이력 API — 검사결과 전송(UC-RST-06, ZP2-120) + 청구 발행(UC-COM-03, ZP2-124)을 한 곳에서 본다.
 *
 * 엔드포인트
 *   GET  /api/lab-imaging/interface-send-logs?eventTypeCode=&sendStatusCode=&from=&to=&page=&size=
 *   GET  /api/lab-imaging/interface-send-logs/{id}          상세(원문 포함)
 *   POST /api/lab-imaging/interface-send-logs/{id}/resend   수동 재전송 (최대 재시도 초과 건 포함)
 *
 * ⚠ 결과전송과 청구를 테이블 두 개로 나누지 않은 이유(설계안) 그대로, 화면도 유형 필터 하나로 본다.
 * ⚠ from/to 는 날짜(yyyy-MM-dd)다. to 는 그날 하루를 포함한다(서버가 다음날 0시 미만으로 바꾼다).
 */
@RestController
@RequestMapping("/api/lab-imaging/interface-send-logs")
@RequiredArgsConstructor
@Tag(name = "연계 발신 이력", description = "UC-RST-06 결과전송 / UC-COM-03 청구 발행 이력·재전송")
public class InterfaceSendLogController {

    private final InterfaceSendLogService interfaceSendLogService;

    @Operation(summary = "발신 이력 조회", description = "생성일시 내림차순, 페이지 조회. 조건은 모두 선택.")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<InterfaceSendLogDto>>> search(
            @RequestParam(required = false) String eventTypeCode,
            @RequestParam(required = false) String sendStatusCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageResponse<InterfaceSendLogDto> response = interfaceSendLogService.search(
                eventTypeCode, sendStatusCode,
                from == null ? null : from.atStartOfDay(),
                to == null ? null : to.plusDays(1).atStartOfDay(),
                page, size);

        return ResponseEntity.ok(ApiResponse.success(response, LabMessageCode.LAB088, "발신 이력 조회에 성공했습니다."));
    }

    @Operation(summary = "발신 이력 상세", description = "발행 원문(payload) 포함")
    @GetMapping("/{interfaceSendLogId}")
    public ResponseEntity<ApiResponse<InterfaceSendLogDto>> getDetail(@PathVariable String interfaceSendLogId) {
        return ResponseEntity.ok(ApiResponse.success(
                interfaceSendLogService.getDetail(interfaceSendLogId), LabMessageCode.LAB088, "발신 이력 조회에 성공했습니다."));
    }

    @Operation(summary = "수동 재전송",
            description = "같은 event_id·같은 원문으로 다시 발행한다. 최대 재시도 초과 건도 가능. "
                    + "이미 완료(02)·원문 없음은 LAB092, Kafka 비활성은 LAB091.")
    @PostMapping("/{interfaceSendLogId}/resend")
    public ResponseEntity<ApiResponse<InterfaceSendLogDto>> resend(@PathVariable String interfaceSendLogId) {
        return ResponseEntity.ok(ApiResponse.success(
                interfaceSendLogService.resend(interfaceSendLogId), LabMessageCode.LAB090, "재전송을 요청했습니다."));
    }
}
