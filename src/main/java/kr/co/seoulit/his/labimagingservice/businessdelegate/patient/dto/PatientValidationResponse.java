package kr.co.seoulit.his.labimagingservice.businessdelegate.patient.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * patient-service의 PatientValidationResponse.
 * (GET /api/v1/patients/{patientId}/validation 응답 — "존재·활성·통합 여부 확인")
 *
 * ⚠ TODO(환자서비스팀 확인 필요): 명세서(Patient_Service_API_명세서_완성본.xlsx)에는
 *   응답 타입명이 PatientValidationResponse 라고만 적혀 있고 필드명이 없다.
 *   아래 두 필드는 명세서의 "상태 변경 공통 원칙"(환자 상태 = ACTIVE/INACTIVE/MERGED)에서
 *   유추한 것이다. 실제 필드명이 확정되면 여기만 고치면 된다.
 *   판정 로직은 PatientServiceHttpBusinessDelegate#isActivePatient 참고.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PatientValidationResponse {

    /** 유효 여부를 그대로 내려주는 경우 */
    private Boolean valid;

    /** 상태코드로 내려주는 경우 — ACTIVE / INACTIVE / MERGED */
    private String statusCd;
}
