package kr.co.seoulit.his.labimagingservice.businessdelegate.patient;

/**
 * Patient Service(환자 서비스) 연동 클라이언트.
 *
 * 구현체: PatientServiceHttpBusinessDelegate (RestTemplate)
 *
 * 참고 API(Patient_Service_API_명세서_완성본.xlsx 기준):
 *   GET /api/patients/{patientId}/validation    환자 유효성 검증(존재·활성·통합 여부)
 *
 * ⚠ 검증은 환자번호(patientNo)가 아니라 내부 식별자(patientId) 기준이다.
 *   2026-08-06 참조 컬럼을 patient_id 로 전환하면서 호출부가 patientId를 직접 넘긴다.
 *   patient_no 는 컬럼만 남아 있고 화면·DTO 어디에서도 쓰지 않는다. (2026-08-25 결정)
 */
public interface PatientServiceBusinessDelegate {

    /**
     * 환자 유효성(존재·활성·통합 여부) 확인.
     *
     * @param patientId 환자ID (patient-service 내부 식별자)
     * @return 유효한 환자면 true
     */
    boolean validatePatient(String patientId);
}
