package kr.co.seoulit.his.labimagingservice.businessdelegate.patient.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * patient-service의 PatientValidationResponse.
 * (GET /api/patients/{patientId}/validation 응답 — "존재·활성·통합 여부 확인")
 *
 * 실제 응답 필드는 2개다. (2026-08-06 환자서비스 담당자 확인)
 *   { "patientId": "...", "valid": true }
 *
 * ⚠ patientId 는 매핑하지 않았다. 우리가 호출할 때 넘긴 값이 그대로 돌아오는 것이라
 *   쓸 데가 없다. 클래스에 없는 필드는 Spring Boot의 Jackson 기본 설정
 *   (FAIL_ON_UNKNOWN_PROPERTIES 비활성)이 조용히 무시한다.
 *   상세는 ExternalApiResponse 주석 참고.
 */
@Getter
@Setter
@NoArgsConstructor
public class PatientValidationResponse {

    /** 환자 유효 여부 (존재·활성·통합 판정 결과) */
    private Boolean valid;
}
