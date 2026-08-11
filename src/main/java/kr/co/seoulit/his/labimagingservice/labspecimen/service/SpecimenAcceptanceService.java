package kr.co.seoulit.his.labimagingservice.labspecimen.service;

import kr.co.seoulit.his.labimagingservice.labspecimen.repository.SpecimenAcceptanceRepository;
import kr.co.seoulit.his.labimagingservice.labspecimen.repository.SpecimenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 검체 인수/적합성 판정 서비스
 * 대응 유스케이스: UC-SPC-04 검체적합성판정 (Jira ZP2-8)
 *
 * TODO: 기능 구현
 *   - 바코드 검증 및 인수 처리 (ZP2-75)
 *   - 적합성 판정 로직 및 상태 변경 (ZP2-78)
 *   - 부적합 사유 등록 및 재채취 요청 처리 (ZP2-74)
 *
 * TODO: 공통코드 검증 연결 — unfitReasonCode 는 SPECIMEN_REJECT_CD 그룹이다.
 *       fitnessStatusCode 는 공통코드가 아니라 서비스 내부 Enum이다 (2026-08-04 재분류 결정).
 */
@Service
@RequiredArgsConstructor
public class SpecimenAcceptanceService {

    private final SpecimenAcceptanceRepository specimenAcceptanceRepository;
    private final SpecimenRepository specimenRepository;
}
