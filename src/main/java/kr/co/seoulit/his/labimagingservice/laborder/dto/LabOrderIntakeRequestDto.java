package kr.co.seoulit.his.labimagingservice.laborder.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 검사오더 연계 수신 요청 (처방코어 → 검사영상서비스)
 * API: POST /api/lab-imaging/lab-orders/intake
 *
 * ⚠ 이 DTO 는 "코어가 보내는 모양" 그대로다. 우리 도메인 용어로 고치지 않았다.
 *   prescriptionId / doctorId / itemCode 처럼 코어 쪽 이름을 그대로 둔 이유가 그것이다.
 *
 * ⚠ 검사 도메인 요청 DTO(LabOrderCreateRequestDto)와 일부러 분리했다.
 *   하나로 합치면 코어 계약이 바뀔 때마다 검사 도메인과 프론트 수동 등록 폼까지 같이 흔들린다.
 *   수신 계약이 바뀌어도 이 파일 하나만 고치면 되도록 경계를 둔 것이다.
 *
 * ⚠ 두 DTO 사이의 변환은 LabOrderIntakeService.toCreateRequest 가 담당한다.
 *   코어에 없는 값(systemCode / treatTypeCode / urgencyYn / receivedById)을 어디서 채우는지도 거기 있다.
 *
 * ⚠ Kafka 로 바뀌어도 이 DTO 는 그대로 쓴다. 메시지 본문 모양이 같기 때문이다.
 *   바뀌는 것은 이걸 받는 입구(Controller → Consumer)뿐이다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "검사오더 연계 수신 요청 (처방코어)")
public class LabOrderIntakeRequestDto {

    @NotBlank
    @Size(max = 36)
    @Schema(description = "처방ID — 검사오더번호로 사용한다(중복 판정 기준)",
            example = "3f7b1a20-6c2e-4e7a-9e2a-8b1f2c3d4e5f",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String prescriptionId;

    @Size(max = 36)
    @Schema(description = "진료건ID — 저장하지 않고 수신 원문에만 남긴다",
            example = "9c8b7a6f-1234-4e5f-9a0b-1c2d3e4f5a6b")
    private String encounterId;

    @NotBlank
    @Size(max = 36)
    @Schema(description = "환자ID (patient-service 내부 식별자)",
            example = "d0a1b2c3-4d5e-6f70-8192-a3b4c5d6e7f8",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String patientId;

    /*
     * ⚠ patientNo 는 받지 않는다. (2026-08-25 결정)
     *   전체 MSA 에서 환자번호 활용 방식이 정해지기 전까지 화면·DTO 어디에서도 쓰지 않기로 했다.
     *   발급 주체도 아직 없어서 코어도, patient-service 도 이 값을 갖고 있지 않다.
     *   환자 식별은 patientId 로 하고, 화면에 필요한 환자명은 그 ID 로 조회한다.
     */

    @Size(max = 36)
    @Schema(description = "처방의ID", example = "a1b2c3d4-5e6f-7081-92a3-b4c5d6e7f809")
    private String doctorId;

    @NotEmpty
    @Valid
    @Schema(description = "검사항목 목록 (최소 1건)", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Item> orderItems;

    /**
     * 검사항목 1건.
     *
     * ⚠ 중첩 클래스로 둔 이유는 이 모양이 오직 수신 계약에서만 쓰이기 때문이다.
     *   밖으로 빼면 검사 도메인의 LabOrderItemRequestDto 와 헷갈린다.
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {

        @NotBlank
        @Size(max = 20)
        @Schema(description = "검사항목코드 (공통코드 TEST_TYPE_CD)",
                example = "CBC", requiredMode = Schema.RequiredMode.REQUIRED)
        private String itemCode;

        /** ⚠ 받되 저장하지 않는다. 표시명은 admin 공통코드를 따른다. (개발표준가이드 14.1 스냅샷 금지) */
        @Size(max = 100)
        @Schema(description = "검사항목명 — 참고용, 저장하지 않음", example = "일반혈액검사")
        private String itemName;
    }
}
