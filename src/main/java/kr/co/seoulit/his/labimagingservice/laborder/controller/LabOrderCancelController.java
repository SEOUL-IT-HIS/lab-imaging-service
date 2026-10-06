package kr.co.seoulit.his.labimagingservice.laborder.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.co.seoulit.his.labimagingservice.common.exception.OrderNotYetReceivedException;
import kr.co.seoulit.his.labimagingservice.laborder.dto.LabOrderCancelRequestDto;
import kr.co.seoulit.his.labimagingservice.laborder.dto.LabOrderCancelResultDto;
import kr.co.seoulit.his.labimagingservice.laborder.service.LabOrderCancelCommand;
import kr.co.seoulit.his.labimagingservice.laborder.service.LabOrderCancelService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 검사오더 취소 연계 수신 API (처방코어 전용) — Kafka 롤백 경로.
 * (05번 지시서 Phase 2-G)
 *
 * ⚠ OPD 가 app.kafka.enabled(messaging-enabled)=false 일 때 쓰는 경로다. 평소에는
 *   LabOrderCancelledConsumer(Kafka)가 같은 LabOrderCancelService 를 부른다 — 두 입구가
 *   갈리면 안 되므로 변환만 하고 판정 로직은 전부 서비스에 있다.
 *
 * ⚠ LabOrderIntakeController 와 똑같이 "업무 결과(성공/부분/전부거절)는 HTTP 200" 규칙을 따른다.
 *   다만 이 API 는 예외가 딱 하나뿐이다 — 오더 자체를 아직 못 찾은 경우(LAB117)만 404.
 *   그래서 intake 컨트롤러처럼 @Valid 실패·본문 파싱 실패까지 지역 핸들러로 모양을 통일하지
 *   않았다 — 그 경우들은 공통 GlobalExceptionHandler(ApiResponse, 400/LAB998)로 내려간다.
 *   이 API 는 OPD 의 "평소에는 Kafka, 장애 시에만 REST" 롤백 경로라 호출 빈도가 낮고,
 *   형식 오류까지 intake 와 완전히 같은 모양으로 맞출 필요는 크지 않다고 판단했다
 *   (요청서가 이 부분까지 명시하지 않아 내린 판단 — 최종 보고에 기재).
 */
@RestController
@RequestMapping("/api/lab-imaging/lab-orders")
@RequiredArgsConstructor
@Tag(name = "검사오더 취소 연계 수신", description = "처방코어 전용 — Kafka 미사용 시 REST 롤백 경로")
public class LabOrderCancelController {

    private final LabOrderCancelService labOrderCancelService;

    @Operation(summary = "검사오더 취소 연계 수신",
            description = "처방코어가 취소한 처방의 검사항목을 취소한다. 전체 취소·일부 취소·전부 거절 모두 "
                    + "업무 결과로 HTTP 200 으로 응답하며 code 로 구분한다. 처방의 오더 자체를 아직 찾지 "
                    + "못한 경우에만 404(LAB117)를 돌려준다 — 접수 이벤트보다 취소 이벤트가 먼저 처리된 경우다.")
    @PostMapping("/cancel")
    public LabOrderCancelResultDto cancel(@Valid @RequestBody LabOrderCancelRequestDto request) {
        return labOrderCancelService.cancel(toCommand(request));
    }

    /** 오더를 아직 찾지 못함(LAB117). intake 컨트롤러와 달리 이 한 가지만 실제 HTTP 오류로 내린다. */
    @ExceptionHandler(OrderNotYetReceivedException.class)
    public ResponseEntity<LabOrderCancelResultDto> handleNotYetReceived(OrderNotYetReceivedException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(LabOrderCancelResultDto.notYetReceived(null, e.getMessage()));
    }

    private LabOrderCancelCommand toCommand(LabOrderCancelRequestDto request) {
        List<LabOrderCancelCommand.Item> items = request.getCancelledItems().stream()
                .map(item -> new LabOrderCancelCommand.Item(item.getItemCode(), item.getLabOrderId()))
                .toList();

        return new LabOrderCancelCommand(
                request.getPrescriptionId(), request.getCancelReason(), request.getCancelledBy(), items);
    }
}
