package kr.co.seoulit.his.labimagingservice.laborder.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 검사오더 연계 수신 결과 (검사영상서비스 → 처방코어)
 *
 * ⚠ 공통 ApiResponse<T> 를 쓰지 않는다. 이 엔드포인트만의 예외다.
 *   코어는 labOrderId 를 응답 "최상위"에서 읽는데, ApiResponse 로 감싸면 data 안으로 들어가
 *   코어 쪽에서 null 이 된다. 코어의 LabOrderResult record 모양에 맞춘 것이다.
 *
 * ⚠ 필드 이름·순서를 코어의 record(code, message, labOrderId)와 일치시켰다.
 *   여기를 바꾸면 코어 코드도 같이 바뀌어야 하므로 임의로 정리하지 말 것.
 */
@Getter
@AllArgsConstructor
@Schema(description = "검사오더 연계 수신 결과")
public class LabOrderIntakeResultDto {

    @Schema(description = "결과 코드 (LabMessageCode 값)", example = "LAB001")
    private String code;

    @Schema(description = "결과 메시지", example = "검사 접수가 생성되었습니다.")
    private String message;

    /** 실패하면 null 이다. 코어는 이 값이 있는지로 성공 여부를 한 번 더 확인할 수 있다. */
    @Schema(description = "생성된 검사오더ID (실패 시 null)",
            example = "3f7b1a20-6c2e-4e7a-9e2a-8b1f2c3d4e5f")
    private String labOrderId;

    public static LabOrderIntakeResultDto success(String labOrderId) {
        return new LabOrderIntakeResultDto(
                LabMessageCode.LAB001, "검사 접수가 생성되었습니다.", labOrderId);
    }

    public static LabOrderIntakeResultDto fail(String code, String message) {
        return new LabOrderIntakeResultDto(code, message, null);
    }
}
