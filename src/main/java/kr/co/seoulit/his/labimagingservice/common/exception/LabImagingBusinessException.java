package kr.co.seoulit.his.labimagingservice.common.exception;

import lombok.Getter;

/**
 * 요청 값이 유효하지 않거나(필수값 누락 등), 조회 대상이 존재하지 않을 때 발생.
 */
@Getter
public class LabImagingBusinessException extends RuntimeException {

    private final String messageCode;

    public LabImagingBusinessException(String messageCode, String message) {
        super(message);
        this.messageCode = messageCode;
    }

    /**
     * 원인 예외를 함께 남기는 생성자.
     *
     * ⚠ ImageFileService(ZP2-108/112)에서 처음 쓰인다 — SeaweedFS 업로드 후 DB 저장이
     *   실패했을 때, 그 원인(DataIntegrityViolationException 등)을 로그에서 추적할 수 있도록
     *   cause 를 보존한다. 기존 생성자는 그대로 두어 다른 호출부에 영향이 없다.
     */
    public LabImagingBusinessException(String messageCode, String message, Throwable cause) {
        super(message, cause);
        this.messageCode = messageCode;
    }
}
