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
}
