package kr.co.seoulit.his.labimagingservice.businessdelegate.patient;

import kr.co.seoulit.his.labimagingservice.businessdelegate.dto.ExternalApiResponse;
import kr.co.seoulit.his.labimagingservice.businessdelegate.patient.dto.PatientDetailResponse;
import kr.co.seoulit.his.labimagingservice.businessdelegate.patient.dto.PatientValidationResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * PatientServiceBusinessDelegate의 RestTemplate 구현체.
 *
 * 호출 흐름은 1단계다.
 *   GET /api/patients/{patientId}/validation → 존재·활성·통합(MERGED) 여부 확인
 *
 * 예전에는 환자번호(patientNo)로 patientId를 먼저 조회하는 2단계였다.
 * 2026-08-06 참조 컬럼을 patient_id 기준으로 전환하면서 호출부가 patientId를 직접 넘기게 되어
 * by-number 조회 단계가 필요 없어졌다. (patient_no 는 컬럼만 남아 있고 쓰지 않는다 — 2026-08-25)
 *
 * ⚠ 오더 접수 1건당 원격 호출이 1회 발생한다. 목록 화면처럼 여러 건을 다뤄야 하면
 *   POST /api/patients/batch-query (일괄 조회)로 바꿔야 한다. (개발표준가이드 14.1 배치 조회)
 *
 * ⚠ 404(= 유효하지 않은 환자)만 false로 흡수한다. 타임아웃/커넥션 거부/5xx 등은 예외를 그대로
 *   전파시켜 접수를 실패시킨다(fail-closed). 환자서비스가 죽었을 때 검증을 통과시켜 버리면
 *   존재하지 않는 환자로 접수가 생성될 수 있기 때문이다.
 *   전파된 예외는 GlobalExceptionHandler가 LAB999(500)로 응답한다.
 */
@Slf4j
@Component
public class PatientServiceHttpBusinessDelegate implements PatientServiceBusinessDelegate {

    private static final String PATIENT_VALIDATION_PATH = "/api/patient/{patientId}/validation";
    /**
     * 환자 상세 조회 경로. (6차, 2-2) patient-service 담당자 확인 완료(2026-09-30) — 그대로다.
     * 기존 validatePatient 와 같은 컨벤션(단수형 "patient", 버전 접두어 없음). 응답 모양은
     * PatientDetailResponse 클래스 주석 참고.
     */
    private static final String PATIENT_DETAIL_PATH = "/api/patient/{patientId}";

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public PatientServiceHttpBusinessDelegate(RestTemplate restTemplate,
                                    @Value("${app.patient-service.base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    @Override
    public boolean validatePatient(String patientId) {
        try {
            ResponseEntity<ExternalApiResponse<PatientValidationResponse>> response = restTemplate.exchange(
                    baseUrl + PATIENT_VALIDATION_PATH,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {
                    },
                    patientId);

            ExternalApiResponse<PatientValidationResponse> body = response.getBody();
            PatientValidationResponse validation = (body == null) ? null : body.getData();

            if (validation == null || validation.getValid() == null) {
                // 200을 받았는데 valid를 읽지 못한 상황 = 응답 계약이 어긋난 것이라
                // 유효 여부를 알 수 없다. fail-closed 정책에 따라 통과시키지 않는다.
                log.warn("환자 유효성 응답에서 valid 를 읽지 못했습니다. patientId={}", patientId);
                return false;
            }
            return validation.getValid();

        } catch (HttpClientErrorException.NotFound e) {
            return false;
        }
    }

    /**
     * @see PatientServiceBusinessDelegate#findGenderCode(String)
     * ⚠ validatePatient 와 달리 어떤 예외든(404 포함) 삼키고 null 을 돌려준다. 성별 조회 실패가
     *   결과 등록을 막으면 안 된다는 것이 이 메서드의 계약이다(fail-open, 인터페이스 주석 참고).
     */
    @Override
    public String findGenderCode(String patientId) {
        try {
            ResponseEntity<ExternalApiResponse<PatientDetailResponse>> response = restTemplate.exchange(
                    baseUrl + PATIENT_DETAIL_PATH,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {
                    },
                    patientId);

            ExternalApiResponse<PatientDetailResponse> body = response.getBody();
            PatientDetailResponse detail = (body == null) ? null : body.getData();
            if (detail == null) {
                log.warn("환자 상세 응답에서 데이터를 읽지 못했습니다. patientId={}", patientId);
                return null;
            }
            return detail.getGenderCd();

        } catch (RestClientException e) {
            // 404·5xx·타임아웃·커넥션 거부·응답 파싱 실패 전부 여기로 온다. 참고범위 판정만 보류될 뿐이므로
            // 결과 등록 자체를 막지 않는다(2-2). 자주 실패한다면 이 로그가 patient-service 쪽 문제를 드러낸다.
            log.warn("환자 성별 조회 실패 — 참고범위 판정 없이 진행합니다. patientId={}, 사유={}",
                    patientId, e.getMessage());
            return null;
        }
    }
}
