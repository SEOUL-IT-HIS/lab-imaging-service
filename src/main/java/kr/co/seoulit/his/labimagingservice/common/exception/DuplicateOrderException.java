package kr.co.seoulit.his.labimagingservice.common.exception;

import lombok.Getter;

/**
 * 오더번호(lab_order_no / image_order_no)가 이미 존재할 때 발생.
 * DB의 UNIQUE 제약(UQ_*) 위반을 애플리케이션 레벨에서 먼저 감지해 던지는 용도.
 */
@Getter
public class DuplicateOrderException extends RuntimeException {

    private final String messageCode;

    public DuplicateOrderException(String messageCode, String message) {
        super(message);
        this.messageCode = messageCode;
    }
}
