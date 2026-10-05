package kr.co.seoulit.his.labimagingservice.imaginginterpretation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 판독 담당자 배정 요청
 * 대응 유스케이스: UC-RD-01 (ZP2-23)
 *
 * ⚠ assignedToId 는 admin EMPLOYEE.EMP_ID(UUID, 36자)다. "STF00099" 같은 업무번호가 아니다
 *   (직원 검증, 2026-10-05, 04번 지시서 Phase 2-C — 예전엔 식별자 형식이 확정 전이라 예시가
 *   업무번호였다). ImageReadingService.assignReading 이 StaffValidator.requireDoctor 로
 *   이 값을 검증한다(모드에 따라 WARN 로그 또는 거절).
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "판독 담당자 배정 요청")
public class ImageReadingAssignRequestDto {

    @NotBlank
    @Size(max = 36)
    @Schema(description = "배정할 판독의ID (admin 직원 empId, UUID)", example = "a1b2c3d4-5e6f-7081-92a3-b4c5d6e7f809",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String assignedToId;
}
