package kr.co.seoulit.his.labimagingservice.labresult.microbiology.service;

import kr.co.seoulit.his.labimagingservice.billing.service.BillingChargeService;
import kr.co.seoulit.his.labimagingservice.labresult.service.LabResultTransmissionService;
import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.cache.CommonCodeCache;
import kr.co.seoulit.his.labimagingservice.common.exception.LabImagingBusinessException;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabOrderItemEntity;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabReceptionEntity;
import kr.co.seoulit.his.labimagingservice.laborder.repository.LabOrderItemRepository;
import kr.co.seoulit.his.labimagingservice.labresult.microbiology.dto.MicrobiologyResultCreateRequestDto;
import kr.co.seoulit.his.labimagingservice.labresult.microbiology.dto.MicrobiologyResultSummaryDto;
import kr.co.seoulit.his.labimagingservice.labresult.microbiology.dto.MicrobiologyResultUpdateRequestDto;
import kr.co.seoulit.his.labimagingservice.labresult.microbiology.dto.MicrobiologySusceptibilityDto;
import kr.co.seoulit.his.labimagingservice.labresult.microbiology.entity.MicrobiologyResultEntity;
import kr.co.seoulit.his.labimagingservice.labresult.microbiology.entity.MicrobiologySusceptibilityEntity;
import kr.co.seoulit.his.labimagingservice.labresult.microbiology.mapper.MicrobiologyResultMapper;
import kr.co.seoulit.his.labimagingservice.labresult.microbiology.repository.MicrobiologyResultRepository;
import kr.co.seoulit.his.labimagingservice.labresult.type.LabResultType;
import kr.co.seoulit.his.labimagingservice.labresult.type.LabResultTypeResolver;
import kr.co.seoulit.his.labimagingservice.labspecimen.entity.FitnessStatus;
import kr.co.seoulit.his.labimagingservice.labspecimen.entity.SpecimenEntity;
import kr.co.seoulit.his.labimagingservice.labspecimen.repository.SpecimenAcceptanceRepository;
import kr.co.seoulit.his.labimagingservice.labspecimen.repository.SpecimenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 미생물검사결과 서비스
 * 대응 유스케이스: UC-RST-02 미생물검사결과등록 (Jira ZP2-14: 87~92)
 *
 * 흐름: 적합 검체 선택 → 배양상태 → (양성) 균종·원인균 → 항생제 감수성 → 관찰소견 → 저장(01) → 확정(02)
 *
 * ── 5차 결정: 결과 1건 = 검사항목 1건 (DDL 변경 없이)
 *   MICROBIOLOGY_RESULT 는 검체 단위라 lab_order_item_id 가 없다. 그런데 워크리스트 진행도(n/m)·청구(항목당 1회)·
 *   결과전송은 전부 항목 단위다. 그래서 등록 시점에 두 가지를 강제해 결과 → 항목을 하나로 정한다.
 *     1) 검체가 속한 접수의 MICROBIOLOGY 유형 항목이 정확히 1개 (0개·2개 이상 → LAB074)
 *     2) 그 접수의 미생물 결과도 1건 (다른 검체로 추가 등록 → LAB075)
 *   ⚠ 알려진 제약: 혈액배양 2세트처럼 한 접수에 미생물 검체가 여러 개인 케이스는 처리하지 못한다(6차 이후 과제).
 *
 * ── 중간/최종보고 (D4): 이력 테이블 없이 01(등록)=중간보고, 02(확정)=최종보고. 수정 시 updated_at 만 바뀐다.
 *
 * ── 확정 규칙은 일반검사(LabResultService)와 같다: 01→02 단방향, 확정 후 수정 불가(LAB040), 재확정 불가(LAB041),
 *   확정자=로그인 사용자(컨트롤러의 ActorIdResolver), 입력자=확정자 금지는 설정(D3).
 *
 * ⚠ 확정 시 청구는 일반검사와 같은 경로(BillingChargeService → 발신 이력, 커밋 후 발행)로 요청한다(5차 Phase 5).
 */
@Service
@RequiredArgsConstructor
public class MicrobiologyResultService {

    private static final String RESULT_STATUS_CD = "RESULT_STATUS_CD";
    private static final String CULTURE_STATUS_CD = "CULTURE_STATUS_CD";
    private static final String ORGANISM_CD = "ORGANISM_CD";
    private static final String ANTIBIOTIC_CD = "ANTIBIOTIC_CD";
    private static final String SUSCEPTIBILITY_RESULT_CD = "SUSCEPTIBILITY_RESULT_CD";

    private static final String STATUS_RECORDED = "01";
    private static final String STATUS_CONFIRMED = "02";

    /**
     * 배양상태 "양성". (CULTURE_STATUS_CD 03 — 2026-09-28 admin 실측: 01 Incubating / 02 Negative / 03 Positive)
     * ⚠ 균종·원인균·감수성은 양성일 때만 받는다. 음성(ZP2-91 명시)뿐 아니라 배양중도 동정 전이라 받지 않는다.
     */
    private static final String CULTURE_POSITIVE = "03";

    private final MicrobiologyResultRepository microbiologyResultRepository;
    private final SpecimenRepository specimenRepository;
    private final SpecimenAcceptanceRepository specimenAcceptanceRepository;
    private final LabOrderItemRepository labOrderItemRepository;
    private final CommonCodeCache commonCodeCache;
    private final LabResultTypeResolver labResultTypeResolver;
    private final MicrobiologyResultMapper microbiologyResultMapper;
    /** 확정 시 청구 요청 (5차 Phase 5) */
    private final BillingChargeService billingChargeService;
    /** 확정 시 결과 전송 — 발신 이력(01) 경유, 커밋 후 발행 (5차 Phase 6) */
    private final LabResultTransmissionService labResultTransmissionService;

    /** D3 입력자=확정자 금지 (일반검사와 같은 설정을 쓴다) */
    @Value("${app.auth.forbid-self-confirm:false}")
    private boolean forbidSelfConfirm;

    // ------------------------------------------------------------------ 등록 (ZP2-87~89, 91)

    @Transactional
    public MicrobiologyResultSummaryDto createResult(MicrobiologyResultCreateRequestDto request) {

        SpecimenEntity specimen = specimenRepository.findById(request.getSpecimenId())
                .orElseThrow(() -> new LabImagingBusinessException(
                        LabMessageCode.LAB020,
                        "등록된 검체 정보를 찾을 수 없습니다. (specimenId=" + request.getSpecimenId() + ")"));

        // 적합 판정된 검체만 (UC-RST-02 기본흐름 1). 미판정·부적합이면 거절.
        boolean fit = specimenAcceptanceRepository.findBySpecimen_SpecimenId(specimen.getSpecimenId())
                .map(a -> a.getFitnessStatusCode() == FitnessStatus.FIT)
                .orElse(false);
        if (!fit) {
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB076,
                    "적합 판정된 검체에만 결과를 등록할 수 있습니다. (specimenId=" + specimen.getSpecimenId() + ")");
        }

        LabReceptionEntity reception = specimen.getLabReception();
        LabOrderItemEntity microItem = findSingleMicrobiologyItem(reception);

        if (microbiologyResultRepository.existsBySpecimen_LabReception_LabReceptionId(reception.getLabReceptionId())) {
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB075,
                    "이 접수에는 이미 미생물 결과가 등록되어 있습니다. (접수번호=" + reception.getReceptionNo() + ")");
        }

        validateContent(request.getCultureStatusCode(), request.getOrganismCode(),
                request.getCausativeYn(), request.getSusceptibilities());
        validateCode(RESULT_STATUS_CD, STATUS_RECORDED, "결과상태코드");

        MicrobiologyResultEntity result = MicrobiologyResultEntity.builder()
                .cultureStatusCode(request.getCultureStatusCode())
                .organismCode(blankToNull(request.getOrganismCode()))
                .causativeYn(blankToNull(request.getCausativeYn()))
                .observationNote(blankToNull(request.getObservationNote()))
                .resultStatusCode(STATUS_RECORDED)
                // 클라이언트 시계를 신뢰하지 않는다. (LabResultService 와 같은 원칙)
                .recordedAt(LocalDateTime.now())
                .recordedById(request.getRecordedById())
                .build();
        result.assignSpecimen(specimen);
        result.replaceSusceptibilities(toEntities(request.getSusceptibilities()));

        MicrobiologyResultEntity saved = microbiologyResultRepository.save(result);
        return microbiologyResultMapper.toResponse(saved, microItem);
    }

    // ------------------------------------------------------------------ 수정 (중간보고 갱신, ZP2-90)

    @Transactional
    public MicrobiologyResultSummaryDto updateResult(String resultId, MicrobiologyResultUpdateRequestDto request) {

        MicrobiologyResultEntity result = findDetailOrThrow(resultId);

        if (STATUS_CONFIRMED.equals(result.getResultStatusCode())) {
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB040,
                    "이미 확정된 결과는 수정할 수 없습니다. (microbiologyResultId=" + resultId + ")");
        }

        validateContent(request.getCultureStatusCode(), request.getOrganismCode(),
                request.getCausativeYn(), request.getSusceptibilities());

        result.modifyResult(request.getCultureStatusCode(),
                blankToNull(request.getOrganismCode()),
                blankToNull(request.getCausativeYn()),
                blankToNull(request.getObservationNote()));
        result.replaceSusceptibilities(toEntities(request.getSusceptibilities()));

        return microbiologyResultMapper.toResponse(result, findMicrobiologyItemOrNull(result.getSpecimen().getLabReception()));
    }

    // ------------------------------------------------------------------ 확정 (최종보고)

    @Transactional
    public MicrobiologyResultSummaryDto confirmResult(String resultId, String confirmedById) {

        MicrobiologyResultEntity result = findDetailOrThrow(resultId);

        if (STATUS_CONFIRMED.equals(result.getResultStatusCode())) {
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB041,
                    "이미 확정된 결과입니다. (확정일시=" + result.getConfirmedAt() + ")");
        }

        validateCode(RESULT_STATUS_CD, STATUS_CONFIRMED, "결과상태코드");

        if (forbidSelfConfirm && confirmedById != null && confirmedById.equals(result.getRecordedById())) {
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB068,
                    "결과 입력자와 확정자가 같습니다. 다른 담당자가 확정해야 합니다. (microbiologyResultId=" + resultId + ")");
        }

        result.confirm(STATUS_CONFIRMED, confirmedById, LocalDateTime.now());

        LabOrderItemEntity microItem = findMicrobiologyItemOrNull(result.getSpecimen().getLabReception());
        // 청구 요청 (항목당 1회, 5차 Phase 5). 항목을 못 찾으면(등록 후 매핑 설정이 바뀐 경우) 청구하지 않고 남긴다.
        if (microItem != null) {
            billingChargeService.requestLabCharge(microItem);
        }
        // 결과 전송 (5차 Phase 6). 항목을 못 찾으면 전송도 하지 않고 WARN 만 남긴다.
        labResultTransmissionService.transmitMicrobiology(result, microItem);
        return microbiologyResultMapper.toResponse(result, microItem);
    }

    // ------------------------------------------------------------------ 조회

    @Transactional(readOnly = true)
    public MicrobiologyResultSummaryDto getResult(String resultId) {
        MicrobiologyResultEntity result = findDetailOrThrow(resultId);
        return microbiologyResultMapper.toResponse(result, findMicrobiologyItemOrNull(result.getSpecimen().getLabReception()));
    }

    /** 접수의 미생물 결과 목록. 0건이 정상인 조회라 예외를 던지지 않는다. (접수당 1건 제약이라 보통 0~1건) */
    @Transactional(readOnly = true)
    public List<MicrobiologyResultSummaryDto> getResultsByReceptionNo(String receptionNo) {
        return microbiologyResultRepository.findDetailByReceptionNo(receptionNo).stream()
                .map(r -> microbiologyResultMapper.toResponse(r, findMicrobiologyItemOrNull(r.getSpecimen().getLabReception())))
                .toList();
    }

    // ------------------------------------------------------------------ 검증 (ZP2-91)

    /**
     * 배양상태와 나머지 입력의 조합을 검증한다.
     *
     *   - 배양상태 필수 + 공통코드
     *   - 양성이 아니면(배양중·음성) 균종·원인균·감수성을 받지 않는다 → LAB077
     *   - 원인균 여부는 균종이 있을 때만 → LAB077
     *   - 감수성은 양성 + 균종이 있을 때만 → LAB077, 항생제 중복 불가 → LAB078
     *   - 균종·항생제·감수성판정 공통코드 검증 → LAB017
     */
    private void validateContent(String cultureStatusCode, String organismCode, String causativeYn,
                                 List<MicrobiologySusceptibilityDto> susceptibilities) {

        validateCode(CULTURE_STATUS_CD, cultureStatusCode, "배양상태코드");

        boolean positive = CULTURE_POSITIVE.equals(cultureStatusCode);
        boolean hasOrganism = hasText(organismCode);
        boolean hasSusceptibility = susceptibilities != null && !susceptibilities.isEmpty();

        if (!positive && (hasOrganism || hasText(causativeYn) || hasSusceptibility)) {
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB077,
                    "배양 양성일 때만 균종·원인균·감수성을 입력할 수 있습니다. (배양상태=" + cultureStatusCode + ")");
        }
        if (hasText(causativeYn) && !hasOrganism) {
            throw new LabImagingBusinessException(LabMessageCode.LAB077, "균종 없이 원인균 여부를 입력할 수 없습니다.");
        }
        if (hasSusceptibility && !hasOrganism) {
            throw new LabImagingBusinessException(LabMessageCode.LAB077, "균종이 동정되기 전에는 감수성을 입력할 수 없습니다.");
        }

        if (hasOrganism) {
            validateCode(ORGANISM_CD, organismCode, "균종코드");
        }

        if (hasSusceptibility) {
            Set<String> seen = new HashSet<>();
            for (MicrobiologySusceptibilityDto s : susceptibilities) {
                if (!seen.add(s.getAntibioticCode())) {
                    throw new LabImagingBusinessException(
                            LabMessageCode.LAB078,
                            "같은 항생제가 중복 입력되었습니다. (항생제코드=" + s.getAntibioticCode() + ")");
                }
                validateCode(ANTIBIOTIC_CD, s.getAntibioticCode(), "항생제코드");
                validateCode(SUSCEPTIBILITY_RESULT_CD, s.getSusceptibilityResultCode(), "감수성판정코드");
            }
        }
    }

    // ------------------------------------------------------------------ 내부

    /** 접수의 MICROBIOLOGY 항목이 정확히 1개여야 한다. (5차 결정, 없거나 여럿이면 LAB074) */
    LabOrderItemEntity findSingleMicrobiologyItem(LabReceptionEntity reception) {
        List<LabOrderItemEntity> microItems = microbiologyItems(reception);
        if (microItems.size() != 1) {
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB074,
                    "이 접수의 미생물 검사항목이 정확히 1건이 아닙니다. (접수번호=" + reception.getReceptionNo()
                            + ", 미생물 항목=" + microItems.size() + "건)");
        }
        return microItems.get(0);
    }

    /**
     * 조회·수정 응답용. 등록 후에 매핑 설정이 바뀌어 항목이 1개가 아니게 됐더라도 조회는 막지 않는다.
     */
    private LabOrderItemEntity findMicrobiologyItemOrNull(LabReceptionEntity reception) {
        List<LabOrderItemEntity> microItems = microbiologyItems(reception);
        return microItems.size() == 1 ? microItems.get(0) : null;
    }

    private List<LabOrderItemEntity> microbiologyItems(LabReceptionEntity reception) {
        return labOrderItemRepository.findByReceptionNo(reception.getReceptionNo()).stream()
                .filter(item -> labResultTypeResolver.resolve(item.getLabItemCode()) == LabResultType.MICROBIOLOGY)
                .toList();
    }

    private MicrobiologyResultEntity findDetailOrThrow(String resultId) {
        return microbiologyResultRepository.findDetailById(resultId)
                .orElseThrow(() -> new LabImagingBusinessException(
                        LabMessageCode.LAB071,
                        "미생물 검사 결과를 찾을 수 없습니다. (microbiologyResultId=" + resultId + ")"));
    }

    private List<MicrobiologySusceptibilityEntity> toEntities(List<MicrobiologySusceptibilityDto> dtos) {
        if (dtos == null) {
            return List.of();
        }
        return dtos.stream()
                .map(d -> MicrobiologySusceptibilityEntity.builder()
                        .antibioticCode(d.getAntibioticCode())
                        .susceptibilityResultCode(d.getSusceptibilityResultCode())
                        .build())
                .toList();
    }

    private void validateCode(String groupCode, String code, String fieldLabel) {
        if (!commonCodeCache.isValid(groupCode, code)) {
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB017,
                    "유효하지 않은 " + fieldLabel + "입니다. (" + groupCode + "=" + code + ")");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String blankToNull(String value) {
        return hasText(value) ? value : null;
    }
}
