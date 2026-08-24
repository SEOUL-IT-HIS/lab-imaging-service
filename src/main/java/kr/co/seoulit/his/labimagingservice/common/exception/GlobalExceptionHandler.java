package kr.co.seoulit.his.labimagingservice.common.exception;

import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.dto.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception e) {
        // TODO: 로깅 연동 (개발표준가이드 15.4 참고 — 원본 예외는 서버 로그에만 남기고 응답에는 노출하지 않음)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(LabMessageCode.LAB999, "처리 중 오류가 발생했습니다."));
    }
}
