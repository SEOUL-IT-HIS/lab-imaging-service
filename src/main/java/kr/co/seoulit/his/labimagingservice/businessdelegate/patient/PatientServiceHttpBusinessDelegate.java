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
import org.springframework.web.client.RestTemplate;

/**
 * PatientServiceBusinessDelegate의 RestTemplate 구현체.
 *
 * 호출 흐름이 2단계인 이유:
 *   검사/영상 도메인은 환자번호(patientNo)만 보유하는데, 유효성 검증 API는 내부 식별자(patientId)
 *   기준이라 바로 부를 수 없다. 그래서
 *     1) GET /api/v1/patients/by-number/{patientNo} → patientId 확보 (404면 존재하지 않는 환자)
 *     2) GET /api/v1/patients/{patientId}/validation → 존재·활성·통합(MERGED) 여부까지 확인
 *   순서로 호출한다. 1단계만으로도 "존재 여부"는 걸러지지만, 비활성/통합된 환자를 잡으려면
 *   2단계가 필요하다 (2026-08 팀 결정).
 *
 * ⚠ 오더 접수 1건당 원격 호출이 2회 발생한다. 목록 화면처럼 여러 건을 다뤄야 하면
 *   POST /api/v1/patients/batch-query (일괄 조회)로 바꿔야 한다. (개발표준가이드 14.1 배치 조회)
 *
 * ⚠ 404(= 유효하지 않은 환자)만 false로 흡수한다. 타임아웃/커넥션 거부/5xx 등은 예외를 그대로
 *   전파시켜 접수를 실패시킨다(fail-closed). 환자서비스가 죽었을 때 검증을 통과시켜 버리면
 *   존재하지 않는 환자로 접수가 생성될 수 있기 때문이다.
 *   전파된 예외는 GlobalExceptionHandler가 LAB999(500)로 응답한다.
 */
@Slf4j
@Component
public class PatientServiceHttpBusinessDelegate implements PatientServiceBusinessDelegate {

    private static final String PATIENT_BY_NUMBER_PATH = "/api/patients/by-number/{patientNo}";
    private static final String PATIENT_VALIDATION_PATH = "/api/patients/{patientId}/validation";

    private static final String STATUS_ACTIVE = "ACTIVE";

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public PatientServiceHttpBusinessDelegate(RestTemplate restTemplate,
                                    @Value("${app.patient-service.host}") String host,
                                    @Value("${app.patient-service.port}") int port) {
        this.restTemplate = restTemplate;
        this.baseUrl = "http://" + host + ":" + port;
    }

    @Override
    public boolean validatePatient(String patientNo) {
        String patientId = findPatientId(patientNo);
        if (patientId == null) {
            log.info("환자번호에 해당하는 환자가 없습니다. patientNo={}", patientNo);
            return false;
        }
        return isActivePatient(patientId, patientNo);
    }

    /** 1단계 — 환자번호로 patientId 조회. 없으면(404) null. */
    private String findPatientId(String patientNo) {
        try {
            ResponseEntity<ExternalApiResponse<PatientDetailResponse>> response = restTemplate.exchange(
                    baseUrl + PATIENT_BY_NUMBER_PATH,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {
                    },
                    patientNo);

            ExternalApiResponse<PatientDetailResponse> body = response.getBody();
            if (body == null || body.getData() == null) {
                log.warn("환자 조회 응답 본문이 비어 있습니다. patientNo={}", patientNo);
                return null;
            }
            return body.getData().getPatientId();

        } catch (HttpClientErrorException.NotFound e) {
            return null;
        }
    }

    /** 2단계 — patientId로 존재·활성·통합 여부 확인. */
    private boolean isActivePatient(String patientId, String patientNo) {
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

            if (validation == null) {
                // 1단계에서 환자 존재는 이미 확인됐고 검증 API도 200을 줬으므로 유효로 본다.
                log.warn("환자 유효성 응답 본문이 비어 있어 존재 여부만으로 판정합니다. patientNo={}", patientNo);
                return true;
            }
            if (validation.getValid() != null) {
                return validation.getValid();
            }
            if (validation.getStatusCd() != null) {
                return STATUS_ACTIVE.equals(validation.getStatusCd());
            }

            // ⚠ TODO: PatientValidationResponse의 실제 필드명이 확정되면 이 fallback은 제거한다.
            //    (필드명이 달라 valid/statusCd 둘 다 못 읽은 상황 — 200 응답 = 유효로 간주)
            log.warn("환자 유효성 응답에서 판정 필드(valid/statusCd)를 찾지 못했습니다. "
                    + "필드명 확인 필요. patientNo={}", patientNo);
            return true;

        } catch (HttpClientErrorException.NotFound e) {
            return false;
        }
    }
}
