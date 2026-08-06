package kr.co.seoulit.his.labimagingservice.businessdelegate.patient;

/**
 * Patient Service(환자 서비스) 연동 클라이언트.
 *
 * 구현체: PatientServiceHttpBusinessDelegate (RestTemplate)
 *
 * 참고 API(Patient_Service_API_명세서_완성본.xlsx 기준 — base path는 /api/v1):
 *   GET /api/patients/by-number/{patientNo}     환자번호 기준 단건 조회
 *   GET /api/patients/{patientId}/validation    환자 유효성 검증(존재·활성·통합 여부)
 *
 * ⚠ 유효성 검증 API는 patientNo가 아니라 내부 식별자 patientId 기준이라,
 *   구현체는 by-number 조회로 patientId를 얻은 뒤 validation을 호출하는 2단계 방식이다.
 */
public interface PatientServiceBusinessDelegate {

    /**
     * 환자번호 유효성(존재 여부) 확인.
     *
     * @param patientNo 환자번호
     * @return 유효한 환자면 true
     */
    boolean validatePatient(String patientNo);
}
