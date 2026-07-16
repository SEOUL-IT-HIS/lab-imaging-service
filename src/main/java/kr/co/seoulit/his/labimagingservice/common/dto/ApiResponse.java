package kr.co.seoulit.his.labimagingservice.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 모든 API 공통 응답 포맷 (개발표준가이드 11.3 공통 API 응답 포맷)
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "공통 API 응답 포맷")
public class ApiResponse<T> {

    @Schema(description = "결과 코드 (개발표준가이드 15.2 메시지 코드 체계, 예: LAB001)", example = "LAB001")
    private String code;

    @Schema(description = "결과 메시지 (사용자 노출용)", example = "검사 접수가 생성되었습니다.")
    private String message;

    @Schema(description = "응답 데이터 (실패 시 null)")
    private T data;

    public static <T> ApiResponse<T> success(T data, String code, String message) {
        return new ApiResponse<>(code, message, data);
    }

    public static <T> ApiResponse<T> fail(String code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
