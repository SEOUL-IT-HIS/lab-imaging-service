package kr.co.seoulit.his.labimagingservice.businessdelegate.patient.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * patient-service의 PatientValidationResponse.
 * (GET /api/patients/{patientId}/validation 응답 — "존재·활성·통합 여부 확인")
 *
 * ⚠ TODO(환자서비스팀 확인 필요): 명세서(Patient_Service_API_명세서_완성본.xlsx)에는
 *   응답 타입명이 PatientValidationResponse 라고만 적혀 있고 필드명이 없다.
 *   아래 두 필드는 명세서의 "상태 변경 공통 원칙"(환자 상태 = ACTIVE/INACTIVE/MERGED)에서
 *   유추한 것이다. 실제 필드명이 확정되면 여기만 고치면 된다.
 *   판정 로직은 PatientServiceHttpBusinessDelegate#isActivePatient 참고.
 *
 * ⚠ 필요한 필드만 선언했다. 클래스에 없는 필드가 응답에 와도 Spring Boot의 Jackson 기본 설정
 *   (FAIL_ON_UNKNOWN_PROPERTIES 비활성)이 조용히 무시한다. 상세는 ExternalApiResponse 주석 참고.
 *    @JsonIgnoreProperties 전역설정이 바뀌면 어노테이션 선언을 별도로 하여 방어한다.
 */
@Getter
@Setter
@NoArgsConstructor
public class PatientValidationResponse {

    /** 유효 여부를 그대로 내려주는 경우 */
    private Boolean valid;

    /** 상태코드로 내려주는 경우 — ACTIVE / INACTIVE / MERGED */
    private String statusCd;
}
