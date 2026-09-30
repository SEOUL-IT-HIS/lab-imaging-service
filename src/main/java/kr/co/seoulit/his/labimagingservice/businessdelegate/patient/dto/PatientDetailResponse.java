package kr.co.seoulit.his.labimagingservice.businessdelegate.patient.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * patient-service의 환자 상세 응답 — 성별코드만 쓴다. (6차, 2-2)
 *
 * 경로·필드 모두 patient-service 담당자 확인 완료(2026-09-30 회신) — 처음 추측했던 그대로였다.
 *   GET /api/patient/{patientId}(버전 접두어 없음, 기존 validatePatient 와 같은 컨벤션) → 응답
 *   data.genderCd(마스킹 없음, 01 남성/02 여성/03 미상/04 기타). 전용 성별 API는 없고, 단건 조회는
 *   이 상세 API 를 그대로 쓰면 된다(회신 확인 — 배치가 필요한 목록 화면이면 POST /api/patient/batch).
 */
@Getter
@Setter
@NoArgsConstructor
public class PatientDetailResponse {

    /** 성별코드: 01 남성 / 02 여성 / 03 미상 / 04 기타 */
    private String genderCd;
}
