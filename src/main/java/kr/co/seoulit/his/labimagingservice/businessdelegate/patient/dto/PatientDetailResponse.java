package kr.co.seoulit.his.labimagingservice.businessdelegate.patient.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * patient-service의 PatientDetailResponse 중 이 서비스가 필요로 하는 필드만 담는다.
 * (GET /api/v1/patients/by-number/{patientNo} 응답)
 *
 * 검사/영상 서비스는 환자번호(patientNo)만 보유하는데 유효성 검증 API는
 * 내부 식별자(patientId) 기준이라, 그 사이를 잇기 위한 최소 DTO다.
 * 나머지 환자 정보는 개발표준가이드 14.1(타 서비스 소유 데이터 스냅샷 금지)에 따라
 * 저장하지도, 매핑하지도 않는다.
 *
 * ⚠ patientId의 실제 JSON 타입(숫자/문자열)이 명세서에 명시돼 있지 않아 String으로 받는다.
 *   Jackson이 숫자도 문자열로 변환해주므로 어느 쪽이 와도 동작한다.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PatientDetailResponse {

    private String patientId;
}
