package kr.co.seoulit.his.labimagingservice.common.exception;

import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.dto.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(LabMessageCode.LAB003, "필수 항목이 누락되었거나 형식이 올바르지 않습니다."));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(LabMessageCode.LAB003, "필수 항목이 누락되었거나 형식이 올바르지 않습니다."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception e) {
        // TODO: 로깅 연동 (개발표준가이드 15.4 참고 — 원본 예외는 서버 로그에만 남기고 응답에는 노출하지 않음)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(LabMessageCode.LAB999, "처리 중 오류가 발생했습니다."));
    }
}
