package kr.co.seoulit.his.labimagingservice.labspecimen.service;

import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.cache.CommonCodeCache;
import kr.co.seoulit.his.labimagingservice.common.exception.LabImagingBusinessException;
import kr.co.seoulit.his.labimagingservice.labspecimen.dto.SpecimenAcceptanceRequestDto;
import kr.co.seoulit.his.labimagingservice.labspecimen.dto.SpecimenAcceptanceSummaryDto;
import kr.co.seoulit.his.labimagingservice.labspecimen.entity.FitnessStatus;
import kr.co.seoulit.his.labimagingservice.labspecimen.entity.SpecimenAcceptanceEntity;
import kr.co.seoulit.his.labimagingservice.labspecimen.entity.SpecimenEntity;
import kr.co.seoulit.his.labimagingservice.labspecimen.mapper.SpecimenAcceptanceMapper;
import kr.co.seoulit.his.labimagingservice.labspecimen.repository.SpecimenAcceptanceRepository;
import kr.co.seoulit.his.labimagingservice.labspecimen.repository.SpecimenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 검체 인수/적합성 판정 서비스
 * 대응 유스케이스: UC-SPC-04 검체적합성판정 (Jira ZP2-8)
 *
 * ⚠ Service 인터페이스 없이 클래스로 바로 구현한다. 사유는 LabOrderService 주석 참고.
 *
 * ⚠ fitnessStatusCode 는 공통코드가 아니라 서비스 내부 Enum이다 (2026-08-04 재분류 결정).
 *   unfitReasonCode 만 공통코드(SPECIMEN_REJECT_CD) 검증 대상이다.
 */
@Service
@RequiredArgsConstructor
public class SpecimenAcceptanceService {

    private static final String SPECIMEN_REJECT_CD = "SPECIMEN_REJECT_CD";
    private static final String YES = "Y";

    private final SpecimenAcceptanceRepository specimenAcceptanceRepository;
    private final SpecimenRepository specimenRepository;
    private final SpecimenAcceptanceMapper specimenAcceptanceMapper;
    private final CommonCodeCache commonCodeCache;

    /**
     * 검체 인수 + 적합성 판정 등록.
     *
     * @param specimenId 판정 대상 검체 (경로변수)
     *
     * 처리 순서
     *   1) 검체 존재 확인 — 없는 검체를 판정할 수는 없다.
     *   2) 중복 판정 차단 — 검체 1건당 판정 1건(1:1)이다.
     *   3) 적합상태와 나머지 값의 앞뒤가 맞는지 검증 (Bean Validation 이 못 잡는 조건부 규칙).
     *   4) 부적합사유코드 공통코드 검증.
     *   5) 저장.
     */
    @Transactional
    public SpecimenAcceptanceSummaryDto acceptSpecimen(String specimenId, SpecimenAcceptanceRequestDto request) {

        SpecimenEntity specimen = specimenRepository.findById(specimenId)
                .orElseThrow(() -> new LabImagingBusinessException(
                        LabMessageCode.LAB020,
                        "등록된 검체 정보를 찾을 수 없습니다. (specimenId=" + specimenId + ")"
                ));

        if (specimenAcceptanceRepository.existsBySpecimen_SpecimenId(specimenId)) {
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB022,
                    "이미 인수/판정이 완료된 검체입니다. (바코드=" + specimen.getSpecimenBarcode() + ")"
            );
        }

        validateJudgment(request);

        SpecimenAcceptanceEntity acceptance = SpecimenAcceptanceEntity.builder()
                .acceptedAt(request.getAcceptedAt())
                .acceptedById(request.getAcceptedById())
                .fitnessStatusCode(request.getFitnessStatus())
                .unfitReasonCode(request.getUnfitReasonCode())
                .recollectionRequestedYn(request.getRecollectionRequestedYn())
                .build();
        acceptance.assignSpecimen(specimen);

        SpecimenAcceptanceEntity saved = specimenAcceptanceRepository.save(acceptance);
        return specimenAcceptanceMapper.toResponse(saved);
    }

    /**
     * 적합상태에 따라 달라지는 규칙을 검증한다.
     *
     * ⚠ "다른 필드 값에 따라 필수/금지" 는 @NotBlank 같은 필드 단위 어노테이션으로 표현할 수 없다.
     *   그래서 Bean Validation(@Valid) 을 통과한 뒤에도 여기서 한 번 더 본다.
     *
     * 규칙
     *   - 부적합인데 사유가 없으면 안 된다. (부적합 사유는 재채취 판단 근거다)
     *   - 적합인데 사유가 붙어 있으면 안 된다. (앞뒤가 맞지 않는 데이터)
     *   - 적합인데 재채취를 요청할 수는 없다.
     */
    private void validateJudgment(SpecimenAcceptanceRequestDto request) {
        boolean unfit = request.getFitnessStatus() == FitnessStatus.UNFIT;
        boolean hasReason = request.getUnfitReasonCode() != null && !request.getUnfitReasonCode().isBlank();

        if (unfit && !hasReason) {
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB998, "부적합 판정에는 부적합사유코드가 필요합니다.");
        }
        if (!unfit && hasReason) {
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB998, "적합 판정에는 부적합사유코드를 입력할 수 없습니다.");
        }
        if (!unfit && YES.equals(request.getRecollectionRequestedYn())) {
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB998, "적합 판정에는 재채취를 요청할 수 없습니다.");
        }

        // 부적합일 때만 도달한다. 위에서 사유 존재를 이미 보장했다.
        if (unfit) {
            validateCode(SPECIMEN_REJECT_CD, request.getUnfitReasonCode(), "부적합사유코드");
        }
    }

    private void validateCode(String groupCode, String code, String fieldLabel) {
        if (!commonCodeCache.isValid(groupCode, code)) {
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB017,
                    "유효하지 않은 " + fieldLabel + "입니다. (" + groupCode + "=" + code + ")"
            );
        }
    }
}
