package kr.co.seoulit.his.labimagingservice.imagingacquisition.service;

import kr.co.seoulit.his.labimagingservice.imagingacquisition.repository.ConsentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 조영제/침습검사 동의 서비스
 * 대응 유스케이스: UC-IMG-05 조영제/침습검사 동의 등록 (Jira ZP2-28)
 *
 * ⚠ Service 인터페이스 없이 클래스로 바로 구현한다. 사유는 LabOrderService 주석 참고.
 *
 * TODO: 기능 구현
 *   - 동의 여부 등록 및 변경 (ZP2-84)
 *   - 등록 필수값 및 유효성 검증 (ZP2-83)
 *   - 검사 진행 전 동의 상태 확인/조회 (ZP2-80)
 *
 * TODO: 공통코드 검증 연결 — consentTypeCode 는 CONSENT_TYPE_CD,
 *       withdrawnReasonCode 는 CONSENT_WITHDRAW_CD 그룹이다.
 *       CommonCodeCache.isValid(...) 로 검증할 것 (LabOrderService.validateCode 참고, 실패 시 LAB017).
 *       두 그룹 모두 admin 에 등록만 돼 있고 아직 쓰는 코드가 없던 상태라, 실제 코드값이
 *       들어가 있는지 먼저 확인할 것.
 */
@Service
@RequiredArgsConstructor
public class ConsentService {

    private final ConsentRepository consentRepository;
}
