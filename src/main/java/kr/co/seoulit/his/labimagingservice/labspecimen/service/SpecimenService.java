package kr.co.seoulit.his.labimagingservice.labspecimen.service;

import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.cache.CommonCodeCache;
import kr.co.seoulit.his.labimagingservice.common.exception.LabImagingBusinessException;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabReceptionEntity;
import kr.co.seoulit.his.labimagingservice.laborder.repository.LabReceptionRepository;
import kr.co.seoulit.his.labimagingservice.labspecimen.dto.SpecimenCreateRequestDto;
import kr.co.seoulit.his.labimagingservice.labspecimen.dto.SpecimenSummaryDto;
import kr.co.seoulit.his.labimagingservice.labspecimen.entity.SpecimenEntity;
import kr.co.seoulit.his.labimagingservice.labspecimen.mapper.SpecimenMapper;
import kr.co.seoulit.his.labimagingservice.labspecimen.repository.SpecimenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

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
 * 환자 검증 PatientServiceBusinessDelegate.validatePatient(patientId) 미사용.
 *       접수 시점에 이미 검증했으므로 중복 호출을 피한다.
 */
@Service
@RequiredArgsConstructor
public class SpecimenService {

    private final SpecimenRepository specimenRepository;
    private final CommonCodeCache commonCodeCache;
    private final SpecimenMapper specimenMapper;
    private final LabReceptionRepository labReceptionRepository;

    @Transactional
    public SpecimenSummaryDto createSpecimen(SpecimenCreateRequestDto request){

        LabReceptionEntity reception = labReceptionRepository.findById(request.getLabReceptionId())
                .orElseThrow(() -> new LabImagingBusinessException(
                        LabMessageCode.LAB013, "검사 접수 정보를 찾을 수 없습니다."));

        validateCode("SPECIMEN_CONTAINER_CD", request.getSpecimenContainerCode(), "검체용기코드");

        SpecimenEntity specimen = SpecimenEntity.builder()
                .specimenBarcode(generateSpecimenBarcode())
                .specimenContainerCode(request.getSpecimenContainerCode())
                .specimenTypeCode(request.getSpecimenType())
                .patientId(request.getPatientId())
                .patientNo(request.getPatientNo())
                .collectedAt(request.getCollectedAt())
                .collectedById(request.getCollectedById())
                .build();
        specimen.assignLabReception(reception);
        SpecimenEntity saved = specimenRepository.save(specimen);
        return specimenMapper.toResponse(saved);

    }
    private void validateCode(String groupCode, String code, String fieldLabel) {
        if (!commonCodeCache.isValid(groupCode, code)) {
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB017,
                    "유효하지 않은 " + fieldLabel + "입니다. (" + groupCode + "=" + code + ")"
            );
        }
    }
    private String generateSpecimenBarcode() {
        return "SP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
