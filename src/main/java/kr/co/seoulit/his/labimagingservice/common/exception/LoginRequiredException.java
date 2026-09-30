package kr.co.seoulit.his.labimagingservice.common.exception;

import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import lombok.Getter;

/**
 * 로그인 사용자가 필요한 작업인데 세션이 없을 때. → 401 (GlobalExceptionHandler)
 *
 * ⚠ app.auth.actor-from-session-required=true 일 때만 발생한다. (D2)
 *   기본값(false, 과도기)에서는 세션이 없으면 요청값을 담당자ID 로 쓰고 WARN 만 남긴다.
 *
 * ⚠ LabImagingBusinessException(400)을 상속하지 않고 따로 둔 이유 —
 *   "요청 값이 틀렸다"(400)와 "누구인지 모른다"(401)는 프론트의 대응이 다르다.
 *   공통 axios 는 401 이면 로그인 화면으로 보낸다(세션 만료 처리).
 */
@Getter
public class LoginRequiredException extends RuntimeException {

    private final String messageCode = LabMessageCode.LAB067;

    public LoginRequiredException(String message) {
        super(message);
    }
}
