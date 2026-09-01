package kr.co.seoulit.his.labimagingservice.labspecimen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kr.co.seoulit.his.labimagingservice.common.YnValue;
import kr.co.seoulit.his.labimagingservice.labspecimen.entity.FitnessStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 검체 인수 + 적합성 판정 요청
 * 대응 유스케이스: UC-SPC-04 검체적합성판정
 * (ZP2-75 바코드 검증·인수, ZP2-78 판정 로직, ZP2-74 부적합 사유·재채취)
 *
 * ⚠ 인수와 판정을 한 요청으로 받는다. SPECIMEN_ACCEPTANCE 는 인수정보와 판정결과가 한 행이고
 *   accepted_at / accepted_by_id / fitness_status_code 가 모두 NOT NULL 이라,
 *   "인수만 하고 판정은 나중에" 라는 중간 상태를 테이블이 표현하지 못한다.
 *   업무 흐름도 "스캔 → 검체 확인 → 적합/부적합 선택 → 저장" 한 동작이다.
 *
 * ⚠ 판정 대상 검체(specimenId)는 이 DTO 가 담지 않고 경로변수로 받는다.
 *   이미 존재하는 검체를 지목하는 행위라, 이 서비스의 재조정 API
 *   (POST /lab-schedules/{labReceptionId}/reschedule) 와 같은 규칙을 따른다.
 *
 * ⚠ unfitReasonCode 는 부적합(UNFIT)일 때만 필수다. Bean Validation 만으로는 이런
 *   "다른 필드 값에 따라 필수" 조건을 표현할 수 없어 Service 에서 검증한다.
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "검체 인수 + 적합성 판정 요청")
public class SpecimenAcceptanceRequestDto {

    @NotNull
    @Schema(description = "검체 인수 일시", example = "2026-07-25T09:30:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime acceptedAt;

    @NotBlank
    @Size(max = 20)
    @Schema(description = "검체인수자ID", example = "STF00021", requiredMode = Schema.RequiredMode.REQUIRED)
    private String acceptedById;

    // enum 필드에는 @Size 가 동작하지 않아 붙이지 않는다. 값 검증은 Jackson 역직렬화가 담당한다.
    @NotNull
    @Schema(description = "적합상태", example = "FIT", requiredMode = Schema.RequiredMode.REQUIRED)
    private FitnessStatus fitnessStatus;

    /** ⚠ 30 인 이유는 SpecimenAcceptanceEntity.unfitReasonCode 주석 참고 (admin 코드값이 10자를 넘는다) */
    @Size(max = 30)
    @Schema(description = "부적합사유코드 (공통코드 SPECIMEN_REJECT_CD, 부적합일 때만)", example = "03")
    private String unfitReasonCode;

    @NotBlank
    @YnValue
    @Schema(description = "재채취요청여부 (Y/N)", example = "N", requiredMode = Schema.RequiredMode.REQUIRED)
    private String recollectionRequestedYn;
}
