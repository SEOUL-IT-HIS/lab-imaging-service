package kr.co.seoulit.his.labimagingservice.labspecimen.service;

import kr.co.seoulit.his.labimagingservice.labspecimen.repository.SpecimenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 검체 식별관리 서비스
 * 대응 유스케이스: UC-SPC-03 검체식별관리 (Jira ZP2-7)
 *
 * ⚠ Service 인터페이스 없이 클래스로 바로 구현한다 (SpecimenServiceImpl 형태 아님).
 *   사유는 LabOrderService 주석 참고.
 *
 * TODO: 기능 구현
 *   - 검체 채취정보 등록 (ZP2-68)
 *   - 검체 식별정보 필수값/유효성 검증 (ZP2-66)
 *   - 검체 바코드 발행 (ZP2-65) — 채번 규칙 확정 필요
 *   - 검체 이력 조회 (ZP2-79)
 *
 * TODO: 공통코드 검증 연결 — specimenContainerCode 는 SPECIMEN_CONTAINER_CD 그룹이다.
 *       CommonCodeCache 를 주입해 isValid("SPECIMEN_CONTAINER_CD", ...) 로 검증할 것.
 *       (LabOrderService.validateCode 참고. 실패 시 LAB017)
 * TODO: 환자 검증이 필요하면 PatientServiceBusinessDelegate.validatePatient(patientId) 사용.
 *       단 접수 시점에 이미 검증했다면 중복 호출은 피할 것.
 */
@Service
@RequiredArgsConstructor
public class SpecimenService {

    private final SpecimenRepository specimenRepository;
}
