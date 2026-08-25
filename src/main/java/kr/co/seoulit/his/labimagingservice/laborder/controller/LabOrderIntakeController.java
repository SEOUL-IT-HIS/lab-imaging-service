package kr.co.seoulit.his.labimagingservice.laborder.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.exception.DuplicateOrderException;
import kr.co.seoulit.his.labimagingservice.common.exception.LabImagingBusinessException;
import kr.co.seoulit.his.labimagingservice.interfacelog.entity.InterfaceOrderType;
import kr.co.seoulit.his.labimagingservice.interfacelog.service.InterfaceReceiveLogService;
import kr.co.seoulit.his.labimagingservice.laborder.dto.LabOrderIntakeRequestDto;
import kr.co.seoulit.his.labimagingservice.laborder.dto.LabOrderIntakeResultDto;
import kr.co.seoulit.his.labimagingservice.laborder.service.LabOrderIntakeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.stream.Collectors;

/**
 * 검사오더 연계 수신 API (처방코어 전용)
 *
 * ⚠ 이 컨트롤러는 프론트가 쓰지 않는다. 처방코어가 서버-서버로 호출한다.
 *   프론트 수동 등록 폼은 기존 LabOrderController(POST /lab-orders)를 그대로 쓴다.
 *   두 입구는 다르지만 결국 같은 LabOrderService.createOrder 로 모인다.
 *
 * ══ 이 컨트롤러만의 규칙 두 가지 ══
 *
 * 1) 업무 실패도 HTTP 200 으로 내리고 code 로 구분한다.
 *    4xx/5xx 로 내리면 코어의 RestTemplate 이 예외를 던지면서 응답 본문을 읽지 않는다.
 *    그러면 코어는 "실패했다"만 알고 "왜 실패했는지"를 볼 수 없다.
 *    연계 수신 API 는 HTTP 상태가 아니라 결과코드로 소통한다.
 *    ⚠ 내부 API 는 지금처럼 400/409 를 유지한다. 이 컨트롤러에만 적용되는 예외다.
 *
 * 2) @ExceptionHandler 를 컨트롤러 안에 둔다.
 *    GlobalExceptionHandler 가 가로채면 응답이 ApiResponse 모양으로 나가 코어와의 계약이 깨진다.
 *    (코어는 labOrderId 를 최상위에서 읽는데 data 안으로 들어가버린다)
 *    스프링은 컨트롤러 지역 핸들러를 전역 핸들러보다 먼저 적용하므로 이렇게 막을 수 있다.
 *
 * ⚠ Kafka 전환 시 이 컨트롤러는 Consumer 로 대체된다.
 *   하지만 LabOrderIntakeService 는 그대로 재사용한다. 변환·저장·로그가 모두 그쪽에 있다.
 */
@RestController
@RequestMapping("/api/lab-imaging/lab-orders")
@RequiredArgsConstructor
@Tag(name = "검사오더 연계 수신", description = "처방코어 전용")
public class LabOrderIntakeController {

    private final LabOrderIntakeService labOrderIntakeService;

    /*
     * ⚠ 아래 둘은 "서비스에 들어가기 전에 튕긴 요청"을 기록하기 위한 것이다.
     *   정상 경로의 기록은 LabOrderIntakeService 가 맡는다. 여기서 또 남기면 행이 두 번 생긴다.
     *   자세한 사유는 logRejected 주석 참고.
     */
    private final InterfaceReceiveLogService interfaceReceiveLogService;
    private final ObjectMapper objectMapper;

    @Operation(summary = "검사오더 연계 수신",
            description = "처방코어가 확정한 검사 처방을 받아 검사 접수를 생성한다. "
                    + "성공·실패 모두 HTTP 200 으로 응답하며 code 로 구분한다. "
                    + "이미 받은 처방이면 LAB004 를 돌려준다.")
    @PostMapping("/intake")
    public LabOrderIntakeResultDto intake(@Valid @RequestBody LabOrderIntakeRequestDto request) {
        return labOrderIntakeService.intake(request);
    }

    /** 이미 접수된 처방 (LAB004). 코어가 같은 처방을 다시 보낸 경우다. */
    @ExceptionHandler(DuplicateOrderException.class)
    public LabOrderIntakeResultDto handleDuplicate(DuplicateOrderException e) {
        return LabOrderIntakeResultDto.fail(e.getMessageCode(), e.getMessage());
    }

    /** 업무 규칙 위반 (환자ID 오류, 공통코드 오류 등). */
    @ExceptionHandler(LabImagingBusinessException.class)
    public LabOrderIntakeResultDto handleBusiness(LabImagingBusinessException e) {
        return LabOrderIntakeResultDto.fail(e.getMessageCode(), e.getMessage());
    }

    /**
     * 요청 형식 오류 (@Valid 실패).
     *
     * ⚠ 어떤 필드가 왜 걸렸는지 담는다. 고정 문구만 내려주면 코어 담당자가 payload 의
     *   어디를 고쳐야 하는지 알 수 없다.
     * ⚠ 다만 코어가 보낸 값 자체는 넣지 않는다. 환자ID 같은 식별자가 응답과 로그에 남는다.
     *   (개발표준가이드 15.1 — GlobalExceptionHandler.handleValidation 과 같은 기준)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public LabOrderIntakeResultDto handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        String message = "필수 항목이 누락되었거나 형식이 올바르지 않습니다. (" + detail + ")";

        // 바인딩까지는 된 객체가 있어 "무엇이 들어왔는지"를 그대로 남길 수 있다.
        logRejected(e.getBindingResult().getTarget(), LabMessageCode.LAB998, message);

        return LabOrderIntakeResultDto.fail(LabMessageCode.LAB998, message);
    }

    /**
     * 본문 자체를 못 읽은 경우 (JSON 문법 오류, 인코딩 깨짐, 타입 불일치 등).
     *
     * ⚠ 실제로 겪은 사례다. Windows PowerShell 에서 curl 로 JSON 을 보내면 큰따옴표가 지워져
     *   깨진 본문이 도착한다. 서버는 멀쩡한데 LAB999 만 돌아와 원인을 찾기 어려웠다.
     *   코어가 필드 타입을 다르게 보내도 같은 자리에서 튕긴다.
     *
     * ⚠ 여기서는 원문을 복구할 수 없다. @RequestBody 파싱이 실패해 객체가 없고,
     *   요청 스트림은 이미 소비됐다. 원문까지 남기려면 ContentCachingRequestWrapper 를 쓰는
     *   필터가 필요한데, 지금은 "도착은 했다"와 파싱 오류 사유만 남겨도 원인 판별에 충분하다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public LabOrderIntakeResultDto handleNotReadable(HttpMessageNotReadableException e) {
        String message = "요청 본문을 읽을 수 없습니다. JSON 형식을 확인해주세요.";

        logRejected(null, LabMessageCode.LAB998, message + " (" + e.getMessage() + ")");

        return LabOrderIntakeResultDto.fail(LabMessageCode.LAB998, message);
    }

    /**
     * Content-Type 이 application/json 이 아닌 경우.
     *
     * ⚠ RuntimeException 핸들러로는 안 잡힌다. 이 예외는 ServletException(검사 예외) 계열이라
     *   그냥 두면 GlobalExceptionHandler 로 넘어가 ApiResponse 모양 + 415 가 나간다.
     *   연계 상대가 헤더를 빠뜨리는 건 흔한 실수라 여기서 막아둔다.
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public LabOrderIntakeResultDto handleMediaType(HttpMediaTypeNotSupportedException e) {
        String message = "Content-Type 은 application/json 이어야 합니다. (받은 값: "
                + e.getContentType() + ")";

        logRejected(null, LabMessageCode.LAB998, message);

        return LabOrderIntakeResultDto.fail(LabMessageCode.LAB998, message);
    }

    /**
     * 서비스에 들어가기 전에 튕긴 요청을 수신 로그에 남긴다.
     *
     * ⚠ 정상 경로에서는 부르지 않는다. LabOrderIntakeService 가 이미 처리 전에 기록하기 때문에,
     *   여기서 또 남기면 같은 요청이 두 행으로 쌓인다.
     *   그래서 "서비스가 확실히 실행되지 않은" 예외 세 가지에서만 호출한다.
     *   (검증 실패 / 본문 파싱 실패 / Content-Type 불일치)
     *
     * ⚠ 이 로그가 없으면 payload 가 안 맞을 때 아무 흔적도 남지 않는다.
     *   @Valid 와 역직렬화는 컨트롤러 메서드 본문보다 먼저 실행돼서,
     *   거기서 튕기면 Service 의 logReceived 까지 도달하지 못한다. (2026-08-25 실제로 겪음)
     *
     * ⚠ 로그를 남기다 실패해도 응답은 정상적으로 나가야 한다. 기록이 응답을 막으면 안 된다.
     */
    private void logRejected(Object payload, String resultCode, String errorMessage) {
        try {
            String logId = interfaceReceiveLogService.logReceived(
                    InterfaceOrderType.LAB,
                    LabOrderIntakeService.SYSTEM_CODE_OUTPATIENT,
                    toRawMessage(payload));
            interfaceReceiveLogService.markResult(logId, resultCode, errorMessage);
        } catch (RuntimeException ignored) {
            // 기록 실패는 응답에 영향을 주지 않는다. 원인은 서버 로그에 남는다.
        }
    }

    private String toRawMessage(Object payload) {
        if (payload == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException e) {
            return "원문 직렬화 실패: " + e.getMessage();
        }
    }

    /**
     * 예상 못 한 실패 (연계 대상 서비스 오류, JSON 파싱 실패, DB 오류 등).
     *
     * ⚠ 요청서에 없던 핸들러다. 실제로 붙여 확인해보니 없으면 규칙이 깨져서 추가했다.
     *   환자서비스가 유효하지 않은 patientId 에 400 을 돌려주면 HttpClientErrorException 이 올라오는데,
     *   이 핸들러가 없으면 GlobalExceptionHandler 가 대신 잡아
     *   ApiResponse 모양({code, message, data})으로 HTTP 500 을 내보낸다. 결과가 둘 다 어긋난다.
     *     - 코어는 labOrderId 를 최상위에서 읽는데 data 안으로 들어가버린다
     *     - RestTemplate 은 5xx 에서 본문을 읽지 않아 실패 사유조차 못 본다
     *   "업무 실패도 200 + code 로 소통한다"는 이 컨트롤러의 규칙은 여기까지 막아야 성립한다.
     *
     * ⚠ 원본 예외 메시지는 코어에 내보내지 않는다. 내부 주소나 SQL 이 섞여 나갈 수 있다.
     *   상세 원인은 서버 로그와 INTERFACE_RECEIVE_LOG.error_message 에 남는다.
     *
     * ⚠ 여기서는 logRejected 를 부르지 않는다. 여기까지 오는 예외는 이미 서비스 안에서 터진 것이라
     *   LabOrderIntakeService 가 LAB999 로 기록을 마친 상태다. 또 남기면 한 요청이 두 행이 된다.
     */
    @ExceptionHandler(RuntimeException.class)
    public LabOrderIntakeResultDto handleUnexpected(RuntimeException e) {
        return LabOrderIntakeResultDto.fail(
                LabMessageCode.LAB999, "처리 중 오류가 발생했습니다.");
    }
}
