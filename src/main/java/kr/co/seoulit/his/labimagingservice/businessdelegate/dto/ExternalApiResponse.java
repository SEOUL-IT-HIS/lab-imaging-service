package kr.co.seoulit.his.labimagingservice.businessdelegate.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 타 서비스(환자/admin) 공통 응답 래퍼.
 *
 * 두 서비스 모두 { code, message, data } 형태를 쓴다.
 *   - patient-service : {"code":200, "message":"SUCCESS", "data":{...}}  ← code가 숫자
 *   - admin-service   : {"code":"OK", "message":"...", "data":{...}}     ← code가 문자열
 * code의 JSON 타입이 서비스마다 달라 필드로 매핑하지 않았다.
 * 이 클라이언트들이 필요로 하는 건 data(+ 실패 시 message)뿐이고,
 * 성공/실패 판정은 응답 본문의 code가 아니라 HTTP 상태코드로 한다.
 *
 * ⚠ 이 서비스 자신의 응답 포맷인 common.dto.ApiResponse 와는 별개다.
 *   (ApiResponse.code는 LAB001 같은 String 메시지코드로, 타 서비스와 체계가 다르다)
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExternalApiResponse<T> {

    private String message;

    private T data;
}
