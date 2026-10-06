package kr.co.seoulit.his.labimagingservice.laborder.controller;

import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.exception.OrderNotYetReceivedException;
import kr.co.seoulit.his.labimagingservice.common.status.CancelOutcome;
import kr.co.seoulit.his.labimagingservice.laborder.dto.ItemCancelResult;
import kr.co.seoulit.his.labimagingservice.laborder.dto.LabOrderCancelRequestDto;
import kr.co.seoulit.his.labimagingservice.laborder.dto.LabOrderCancelResultDto;
import kr.co.seoulit.his.labimagingservice.laborder.service.LabOrderCancelCommand;
import kr.co.seoulit.his.labimagingservice.laborder.service.LabOrderCancelService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 검사오더 취소 연계 수신 REST API (05번 지시서 2-G, Phase 6).
 *
 * ⚠ LabOrderControllerTest 와 같은 스타일 — MockMvc 없이 컨트롤러 메서드/지역 @ExceptionHandler
 *   메서드를 직접 호출한다. 이 API 는 OPD 의 messaging-enabled=false 롤백 경로이므로
 *   실제 호출 빈도가 낮아, 가벼운 단위 테스트로 계약(200 성공/부분/전부거절, 404 LAB117)만 확인한다.
 */
class LabOrderCancelControllerTest {

    private final LabOrderCancelService labOrderCancelService = mock(LabOrderCancelService.class);
    private final LabOrderCancelController controller = new LabOrderCancelController(labOrderCancelService);

    private static LabOrderCancelRequestDto request() {
        return new LabOrderCancelRequestDto(
                "RX-1", "오처방", "DOC-1",
                List.of(new LabOrderCancelRequestDto.Item("CBC", "일반혈액검사", "LO-1")));
    }

    @Test
    @DisplayName("성공(전체 취소) — HTTP 200, 전달 명령이 요청 DTO 의 필드와 일치한다")
    void cancelSuccess() {
        LabOrderCancelResultDto success = LabOrderCancelResultDto.of("RX-1", CancelOutcome.CANCELLED,
                List.of(LabOrderCancelResultDto.ItemResult.of("CBC", ItemCancelResult.CANCELLED, "취소되었습니다.")));
        when(labOrderCancelService.cancel(any())).thenReturn(success);

        LabOrderCancelResultDto result = controller.cancel(request());

        assertThat(result.getCode()).isEqualTo(LabMessageCode.LAB118);
        assertThat(result.getOutcome()).isEqualTo(CancelOutcome.CANCELLED);

        ArgumentCaptor<LabOrderCancelCommand> captor = ArgumentCaptor.forClass(LabOrderCancelCommand.class);
        verify(labOrderCancelService).cancel(captor.capture());
        assertThat(captor.getValue().prescriptionId()).isEqualTo("RX-1");
        assertThat(captor.getValue().cancelReason()).isEqualTo("오처방");
        assertThat(captor.getValue().cancelledBy()).isEqualTo("DOC-1");
        assertThat(captor.getValue().items()).extracting(LabOrderCancelCommand.Item::itemCode).containsExactly("CBC");
    }

    @Test
    @DisplayName("전부 거절이어도 업무 결과라 HTTP 200으로 그대로 돌려준다 (예외가 아니다)")
    void allRefusedStillReturns200() {
        LabOrderCancelResultDto refused = LabOrderCancelResultDto.of("RX-1", CancelOutcome.REFUSED,
                List.of(LabOrderCancelResultDto.ItemResult.of(
                        "CBC", ItemCancelResult.REFUSED_DONE, "이미 결과가 등록되어 취소할 수 없습니다.")));
        when(labOrderCancelService.cancel(any())).thenReturn(refused);

        LabOrderCancelResultDto result = controller.cancel(request());

        assertThat(result.getCode()).isEqualTo(LabMessageCode.LAB120);
        assertThat(result.getOutcome()).isEqualTo(CancelOutcome.REFUSED);
    }

    @Test
    @DisplayName("오더를 아직 찾지 못하면 지역 핸들러가 404 + LAB117 로 응답한다")
    void orderNotYetReceivedReturns404() {
        OrderNotYetReceivedException e = new OrderNotYetReceivedException("해당 처방의 오더가 아직 접수되지 않았습니다.");

        ResponseEntity<LabOrderCancelResultDto> response = controller.handleNotYetReceived(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(LabMessageCode.LAB117);
        assertThat(response.getBody().getOutcome()).isNull();
        assertThat(response.getBody().getItems()).isEmpty();
    }
}
