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

import java.util.List;
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
 *   - 미판정 검체 목록 조회 (ZP2-79, UC-SPC-04 적합성판정 대상)
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
    public SpecimenSummaryDto createSpecimen(SpecimenCreateRequestDto request) {

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

    // ------ 미판정 검체 목록 조회 (적합성판정 대상) ------
    /**
     * 판정등록 대상 = "아직 인수/적합성 판정이 없는(미판정)" 검체 목록.
     * - 조회 전용이라 @Transactional(readOnly = true).
     * - 결과 0건은 정상적인 빈 목록이므로 예외를 던지지 않고 [] 를 그대로 반환한다.
     *   (단건 조회는 "못 찾음 = 예외"가 맞지만, 목록은 빈 결과가 정상 케이스다.)
     * - 미판정 필터와 N+1(join fetch) 방어 설명은 findUnjudgedWithLabReception() 주석 참고.
     *
     * TODO(후속): 접수번호/채취일자/검체종류 등 검색조건, 페이지네이션(Pageable) 필요 시 확장.
     */
    @Transactional(readOnly = true)
    public List<SpecimenSummaryDto> getUnjudgedSpecimens() {
        List<SpecimenEntity> specimens = specimenRepository.findUnjudgedWithLabReception();

        return specimenMapper.toResponseList(specimens);
    }


    // ------ 검체 단건 조회 ------
    @Transactional(readOnly = true)
    public SpecimenSummaryDto getSpecimenById(String specimenId) {
        SpecimenEntity specimen = specimenRepository.findById(specimenId)
                .orElseThrow(() -> new LabImagingBusinessException(
                        LabMessageCode.LAB020,
                        "등록된 검체 정보를 찾을 수 없습니다. (specimenId=" + specimenId + ")"
                ));
        return  specimenMapper.toResponse(specimen);
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