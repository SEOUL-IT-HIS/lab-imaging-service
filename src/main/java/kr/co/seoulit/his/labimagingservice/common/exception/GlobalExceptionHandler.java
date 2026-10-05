package kr.co.seoulit.his.labimagingservice.common.exception;

import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.dto.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.stream.Collectors;

/**
 * 공통 예외 처리
 * - 시스템 예외(스택트레이스 등)는 사용자 응답에 그대로 노출하지 않는다. (개발표준가이드 15.1)
 * - 응답은 항상 ApiResponse<T> 형식을 따른다. (21.8)
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DuplicateOrderException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateOrder(DuplicateOrderException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail(e.getMessageCode(), e.getMessage()));
    }

    @ExceptionHandler(LabImagingBusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(LabImagingBusinessException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(e.getMessageCode(), e.getMessage()));
    }

    /**
     * 로그인 사용자 필요 (LAB067, 5차 Phase 2 / D2).
     * ⚠ 401 로 내려야 공통 axios 가 "세션 만료"로 보고 로그인 화면으로 보낸다.
     */
    @ExceptionHandler(LoginRequiredException.class)
    public ResponseEntity<ApiResponse<Void>> handleLoginRequired(LoginRequiredException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.fail(e.getMessageCode(), e.getMessage()));
    }

    /**
     * @Valid 검증 실패 (요청 본문 DTO).
     *
     * ⚠ 어떤 필드가 왜 걸렸는지를 응답에 담는다.
     *   고정 문구만 내보내면 화면에도 로그에도 단서가 남지 않아, 개발 중에 원인을 찾을 수 없다.
     *   실제로 patientId 가 null 이라 등록이 막힌 건을 추적하는 데 한참 걸렸다. (2026-08-24)
     *
     * ⚠ 담는 것은 "필드명 + 검증 문구"까지다. 사용자가 보낸 값은 넣지 않는다.
     *   요청 값에는 환자ID 같은 식별자가 들어 있어 그대로 돌려주면 로그·화면에 남는다.
     *   (개발표준가이드 15.1 — 시스템 내부 정보를 응답에 노출하지 않는다)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(LabMessageCode.LAB998,
                        "필수 항목이 누락되었거나 형식이 올바르지 않습니다. (" + detail + ")"));
    }

    /**
     * @ModelAttribute + @Valid 검증 실패 중 순수 BindException 만 여기로 온다.
     *
     * ⚠ 실제로는 이 핸들러가 거의 호출되지 않는다. Spring 6 부터
     *   MethodArgumentNotValidException 이 BindException 을 상속하도록 바뀌어서,
     *   @ModelAttribute(폼/멀티파트) + @Valid 실패도 실제로는 MethodArgumentNotValidException 으로
     *   던져지고, 위 handleValidation 이 더 구체적인 타입이라 먼저 매칭돼 그쪽이 처리한다.
     *   (이 프로젝트 최초의 멀티파트 업로드인 ImageFileUploadRequestDto 로 직접 확인함 —
     *    로그에 실제로 MethodArgumentNotValidException 이 찍혔다)
     *   그래도 이 핸들러를 남겨 두는 이유는, MethodArgumentNotValidException 이 아닌 다른 경로로
     *   순수 BindException 이 나는 경우(예: 타입 변환 실패 등)의 방어용이다. 없으면 그런 경우
     *   handleUnknown 이 잡아 "필드명: 사유" 상세 없이 LAB999(500)만 나간다.
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBind(BindException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(LabMessageCode.LAB998,
                        "필수 항목이 누락되었거나 형식이 올바르지 않습니다. (" + detail + ")"));
    }

    /** 경로변수·쿼리파라미터 등 DTO 밖의 검증 실패. 위와 같은 이유로 위반 내용을 담는다. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException e) {
        String detail = e.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining(", "));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(LabMessageCode.LAB998,
                        "필수 항목이 누락되었거나 형식이 올바르지 않습니다. (" + detail + ")"));
    }

    /**
     * 업로드 파일 크기 초과. (LAB109, 04번 지시서 Phase 3-D)
     *
     * ⚠ 413(Payload Too Large)으로 내린다. MultipartConfigElement(공통 설정) 상한을 넘으면
     *   컨트롤러 메서드에 진입하기 전에 Spring 이 이 예외를 던진다 — 서비스 계층의 개별 상한
     *   검사(영상 업로드·병리 첨부, 같은 LAB109)와는 별개의 경로지만 같은 코드로 응답해
     *   프론트가 코드 하나만 처리하면 되게 한다.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.fail(LabMessageCode.LAB109, "파일 크기가 허용 범위를 넘었습니다."));
    }

    /**
     * 요청 본문을 읽을 수 없음(JSON 문법 오류, 날짜 형식 오류 등). (04번 지시서 Phase 3-E-4)
     *
     * ⚠ 요청 값은 응답에 싣지 않는다 — 원문에 환자ID 등 식별자가 섞여 있을 수 있다
     *   (개발표준가이드 15.1, handleValidation 과 같은 기준).
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(LabMessageCode.LAB998, "요청 형식이 올바르지 않습니다."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception e) {
        // TODO: 로깅 연동 (개발표준가이드 15.4 참고 — 원본 예외는 서버 로그에만 남기고 응답에는 노출하지 않음)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(LabMessageCode.LAB999, "처리 중 오류가 발생했습니다."));
    }
}
