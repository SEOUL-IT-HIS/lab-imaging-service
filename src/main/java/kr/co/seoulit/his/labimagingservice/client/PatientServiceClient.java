package kr.co.seoulit.his.labimagingservice.client;

/**
 * Patient Service(환자 서비스) 연동 클라이언트.
 *
 * ⚠ 현재는 뼈대(인터페이스)만 존재하며 구현체가 없습니다.
 *    Patient Service의 실제 API가 준비되면 RestTemplate/WebClient/OpenFeign 등으로
 *    구현체(예: PatientServiceHttpClient)를 만들어 Bean으로 등록해야 합니다.
 *    그 전까지는 이 인터페이스를 주입받아 호출하는 코드를 작성하면 컴파일은 되지만
 *    실행 시 Bean을 찾지 못해 오류가 납니다 — 실제 연동 시점 전까지는 Service 계층에서
 *    호출하지 않는 것을 권장합니다 (TODO 주석으로만 표시).
 *
 * 참고 API(Patient_Service_API_명세서_완성본.xlsx 기준):
 *   GET /api/patients/{patientId}/validation
 */
public interface PatientServiceClient {

    /**
     * 환자번호 유효성(존재 여부) 확인.
     *
     * @param patientNo 환자번호
     * @return 유효한 환자면 true
     */
    boolean validatePatient(String patientNo);
}
