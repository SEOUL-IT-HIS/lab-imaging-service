package kr.co.seoulit.his.labimagingservice.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 모든 API 공통 응답 포맷 (개발표준가이드 21.8 API 설계 원칙)
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "공통 API 응답 포맷")
public class ApiResponse<T> {

    @Schema(description = "처리 성공 여부", example = "true")
    private boolean success;

    @Schema(description = "응답 데이터 (실패 시 null)")
    private T data;

    @Schema(description = "메시지 코드 (개발표준가이드 15.2, 예: LAB001)", example = "LAB001")
    private String messageCode;

    @Schema(description = "메시지 내용 (사용자 노출용)", example = "검사 접수가 생성되었습니다.")
    private String message;

    public static <T> ApiResponse<T> success(T data, String messageCode, String message) {
        return new ApiResponse<>(true, data, messageCode, message);
    }

    public static <T> ApiResponse<T> fail(String messageCode, String message) {
        return new ApiResponse<>(false, null, messageCode, message);
    }
}
