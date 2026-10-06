package kr.co.seoulit.his.labimagingservice.labresult.service;

import kr.co.seoulit.his.labimagingservice.billing.service.BillingChargeService;
import kr.co.seoulit.his.labimagingservice.businessdelegate.patient.PatientServiceBusinessDelegate;
import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.cache.CommonCodeCache;
import kr.co.seoulit.his.labimagingservice.common.exception.LabImagingBusinessException;
import kr.co.seoulit.his.labimagingservice.common.status.ReceptionStatus;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabOrderEntity;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabOrderItemEntity;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabReceptionEntity;
import kr.co.seoulit.his.labimagingservice.laborder.repository.LabOrderItemRepository;
import kr.co.seoulit.his.labimagingservice.laborder.repository.LabReceptionRepository;
import kr.co.seoulit.his.labimagingservice.labresult.dto.LabResultCreateRequestDto;
import kr.co.seoulit.his.labimagingservice.labresult.dto.LabResultDetailRequestDto;
import kr.co.seoulit.his.labimagingservice.labresult.dto.LabResultEntryItemDto;
import kr.co.seoulit.his.labimagingservice.labresult.dto.LabResultItemDto;
import kr.co.seoulit.his.labimagingservice.labresult.dto.LabResultSummaryDto;
import kr.co.seoulit.his.labimagingservice.labresult.dto.LabResultUpdateRequestDto;
import kr.co.seoulit.his.labimagingservice.labresult.entity.LabReferenceRangeEntity;
import kr.co.seoulit.his.labimagingservice.labresult.entity.LabResultDetailEntity;
import kr.co.seoulit.his.labimagingservice.labresult.entity.LabResultEntity;
import kr.co.seoulit.his.labimagingservice.labresult.entity.LabResultItemRuleEntity;
import kr.co.seoulit.his.labimagingservice.labresult.mapper.LabResultMapper;
import kr.co.seoulit.his.labimagingservice.labresult.repository.LabReferenceRangeRepository;
import kr.co.seoulit.his.labimagingservice.labresult.repository.LabResultDetailRepository;
import kr.co.seoulit.his.labimagingservice.labresult.repository.LabResultItemRuleRepository;
import kr.co.seoulit.his.labimagingservice.labresult.repository.LabResultRepository;
import kr.co.seoulit.his.labimagingservice.labresult.type.LabResultType;
import kr.co.seoulit.his.labimagingservice.labresult.type.LabResultTypeResolver;
import kr.co.seoulit.his.labimagingservice.labspecimen.entity.SpecimenAcceptanceEntity;
import kr.co.seoulit.his.labimagingservice.labspecimen.entity.SpecimenEntity;
import kr.co.seoulit.his.labimagingservice.labspecimen.repository.SpecimenAcceptanceRepository;
import kr.co.seoulit.his.labimagingservice.labspecimen.repository.SpecimenRepository;
import kr.co.seoulit.his.labimagingservice.labspecimen.service.SpecimenReadiness;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 일반검사 결과 서비스
 * 대응 유스케이스: UC-RST-01 일반검사결과등록 (Jira ZP2-13)
 *
 * ⚠ Service 인터페이스 없이 클래스로 바로 구현한다. 사유는 LabOrderService 주석 참고.
 *
 * ── 구현 현황
 *   ZP2-100 수기 입력 등록        : 완료 (createLabResult)
 *   ZP2-103 필수값/유효성 검증    : 완료 (DTO Bean Validation + 아래 검증들)
 *   ZP2-99  기준값·참고범위 적용  : 완료 (decideAbnormalYn) — 다만 기준값 마스터가 없다(아래 참고)
 *   ZP2-101 통합 저장·이력 관리   : 완료 (updateLabResult / confirmLabResult)
 *
 * ⚠ 장비 연동 수신은 만들지 않는다. 수기 입력만 있다. (2026-08-31 범위 결정)
 *
 * ── 가정 (ZP2-101 "이력 관리"의 해석)
 *   현재 스키마에는 결과 이력 테이블이 없고, LAB_RESULT 한 행에 confirmed_at / confirmed_by_id 만 있다.
 *   그래서 "이력 관리"를 "수정 전 값을 별도 테이블에 쌓는 것"이 아니라
 *   "등록(01) → 확정(02) 상태 전이를 관리하고, 확정 이후에는 값을 못 바꾸게 막는 것"으로 해석했다.
 *   근거는 두 가지다.
 *     1) 확정 전에는 아직 검토 중인 값이라 남길 이력이랄 게 없다.
 *     2) 확정 후 수정을 막으면 "확정된 값은 바뀌지 않는다"가 보장되어, 이력 테이블 없이도
 *        결과의 신뢰 구간이 생긴다.
 *   ⚠ 이 해석이 틀렸다면(확정 후에도 정정이 필요하고 그 이력을 남겨야 한다면) 스키마부터 바뀌어야 한다.
 *     그건 이번 범위가 아니라 임의로 테이블을 만들지 않았다.
 *
 * ── 결과항목(상세) — 6차 (2026-09-30)
 *   일반검사(01~04)는 검사 1건에 결과항목이 1~3개다(백혈구/적혈구/혈소판처럼). 검사에
 *   LAB_RESULT_ITEM_RULE(use_yn='Y') 행이 있으면 "결과항목 방식", 없으면 지금까지의 "기존 방식"이다.
 *   두 방식은 같은 API(createLabResult/updateLabResult)를 쓰되 요청의 details 유무로 갈린다.
 *   ⚠ 결과항목 방식은 LAB_RESULT.result_value/unit/reference_range 를 전부 NULL 로 둔다.
 *     실제 값은 LAB_RESULT_DETAIL 에 항목별로 들어가고, 헤더 abnormal_yn 은 "상세 중 하나라도
 *     이상이면 Y"로 집계한 값이다.
 *   ⚠ 참고범위·단위는 클라이언트가 보내지 않는다(요청 DTO 에 필드 자체가 없다) — 서버가
 *     LAB_RESULT_ITEM_RULE.default_unit 과 LAB_REFERENCE_RANGE(환자 성별 적용)에서 가져온다.
 *     "클라이언트가 보낸 값을 대체값으로 쓴다"는 2-2 문구는 결과항목 규칙 자체가 없는 검사,
 *     즉 기존 방식에 대한 설명으로 해석했다 — 기존 방식은 지금까지처럼 요청값을 그대로 쓴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LabResultService {

    /** 공통코드 그룹 — admin 에 01=등록, 02=확정으로 등록되어 있어야 한다. */
    private static final String RESULT_STATUS_CD = "RESULT_STATUS_CD";
    /** 공통코드 그룹 — 결과항목코드 (6차) */
    private static final String RESULT_ITEM_CD = "RESULT_ITEM_CD";

    /** 결과상태: 등록(입력만 된 상태, 수정 가능) */
    private static final String STATUS_RECORDED = "01";
    /** 결과상태: 확정(더 이상 수정 불가) */
    private static final String STATUS_CONFIRMED = "02";

    /** LAB_REFERENCE_RANGE.sex_code 의 공통값. 환자 성별(01/02)에 맞는 행이 없을 때 이 행을 쓴다. */
    private static final String SEX_ALL = "ALL";
    /** 결과항목 개수 상한 (2-2, "시스템 상한 4개") */
    private static final int MAX_DETAILS = 4;

    private static final String YES = "Y";
    private static final String NO = "N";

    private final LabResultRepository labResultRepository;
    private final LabOrderItemRepository labOrderItemRepository;
    private final LabReceptionRepository labReceptionRepository;
    /** 결과 등록 전 적합성 판정 확인용 (assertSpecimenReady, 후속조치 #10) */
    private final SpecimenRepository specimenRepository;
    private final SpecimenAcceptanceRepository specimenAcceptanceRepository;
    private final LabResultMapper labResultMapper;
    private final CommonCodeCache commonCodeCache;
    /** 청구 요청 — 발신 이력(INTERFACE_SEND_LOG) 경유, 커밋 후 발행 (5차 Phase 5) */
    private final BillingChargeService billingChargeService;
    /** 확정 시 결과 전송 — 발신 이력(01) 경유, 커밋 후 발행 (5차 Phase 6) */
    private final LabResultTransmissionService labResultTransmissionService;
    /** 검사항목 → 결과유형(일반/미생물/병리). 5차 D1 */
    private final LabResultTypeResolver labResultTypeResolver;
    /** 검사별 결과항목 기준 (6차) */
    private final LabResultItemRuleRepository labResultItemRuleRepository;
    /** 결과항목별 참고범위, 성별 구분 (6차) */
    private final LabReferenceRangeRepository labReferenceRangeRepository;
    /** 목록 조회에서 결과항목을 배치로 붙이기 위한 조회 전용 (N+1 방지, 6차) */
    private final LabResultDetailRepository labResultDetailRepository;
    /** 결과항목 참고범위에 적용할 환자 성별 조회 (6차, 2-2) */
    private final PatientServiceBusinessDelegate patientServiceBusinessDelegate;

    /**
     * D3 입력자=확정자 금지 스위치. 기본 false(막지 않음 — 시연 환경 1인).
     * ⚠ 기본값을 @Value 안에 둬서 설정이 없어도 기동된다. (단위 테스트는 생성자로 만들어 false 그대로)
     */
    @Value("${app.auth.forbid-self-confirm:false}")
    private boolean forbidSelfConfirm;

    // ------------------------------------------------------------------
    // ZP2-100 수기 입력 등록
    // ------------------------------------------------------------------

    /**
     * 검사 결과를 등록한다.
     *
     * 처리 순서
     *   1) 검사항목 존재 확인 — 없는 항목에 결과를 붙일 수는 없다. (ZP2-103)
     *   2) 중복 등록 차단 — 검사항목 1건당 결과 1건(1:1)이다. (ZP2-103)
     *   3) 결과상태 공통코드 검증 — 아래 주석 참고. (ZP2-103)
     *   4) 이 검사에 결과항목 규칙이 있는지에 따라 두 방식 중 하나로 저장한다. (6차, 2-2)
     *   5) 저장.
     *
     * ⚠ 상태는 요청값이 아니라 "01"(등록)로 강제한다. 등록이 곧 확정이 되면
     *   확정 API 가 의미를 잃고, 검토 없이 결과가 확정되는 경로가 생긴다.
     *
     * ⚠ 그런데도 공통코드 검증을 하는 이유 —
     *   검증 대상은 사용자 입력이 아니라 "admin 에 RESULT_STATUS_CD 가 제대로 등록돼 있는가"다.
     *   등록이 안 돼 있으면 결과는 저장되는데 화면에서는 상태를 해석하지 못하는 상태가 된다.
     *   여기서 걸러 두면 첫 등록 시도에서 LAB017 로 바로 드러난다.
     */
    @Transactional
    public LabResultSummaryDto createLabResult(LabResultCreateRequestDto request) {

        LabOrderItemEntity labOrderItem = labOrderItemRepository.findById(request.getLabOrderItemId())
                .orElseThrow(() -> new LabImagingBusinessException(
                        LabMessageCode.LAB035,
                        "검사항목 정보를 찾을 수 없습니다. (labOrderItemId=" + request.getLabOrderItemId() + ")"
                ));

        labOrderItem.requireNotCancelled();

        if (labResultRepository.existsByLabOrderItem_LabOrderItemId(request.getLabOrderItemId())) {
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB036,
                    "이미 결과가 등록된 검사항목입니다. (검사항목코드=" + labOrderItem.getLabItemCode() + ")"
            );
        }

        /*
         * 일반검사 항목에만 이 API 로 결과를 받는다. (5차 D1)
         * ⚠ 미생물·병리 항목에 일반 결과를 넣으면 한 항목에 결과가 두 곳(LAB_RESULT + MICROBIOLOGY/PATHOLOGY_RESULT)
         *   생겨 진행도·청구·결과전송이 두 번 셈해진다.
         */
        LabResultType type = labResultTypeResolver.resolve(labOrderItem.getLabItemCode());
        if (type != LabResultType.GENERAL) {
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB079,
                    "일반검사 결과로 등록할 수 없는 항목입니다. (검사항목코드=" + labOrderItem.getLabItemCode()
                            + ", 유형=" + type + ")");
        }

        assertSpecimenReady(labOrderItem.getLabOrder());

        validateCode(RESULT_STATUS_CD, STATUS_RECORDED, "결과상태코드");

        List<LabResultItemRuleEntity> itemRules =
                labResultItemRuleRepository.findByTestTypeCodeAndUseYnOrderByItemSeqAsc(
                        labOrderItem.getLabItemCode(), YES);

        LabResultEntity labResult;
        if (itemRules.isEmpty()) {
            // 기존 방식 — 규칙이 없는 검사. 동작은 6차 이전과 동일하다(회귀 금지).
            rejectIfDetailsSentToUnsupportedTest(request.getDetails(), labOrderItem.getLabItemCode());
            requireResultValue(request.getResultValue());
            validateNumericResultIfApplicable(request.getResultValue(), request.getReferenceRange(), "resultValue");

            labResult = LabResultEntity.builder()
                    .resultValue(request.getResultValue())
                    .resultUnit(request.getResultUnit())
                    .referenceRange(request.getReferenceRange())
                    .abnormalYn(AbnormalYnDecider.decide(request.getResultValue(), request.getReferenceRange()))
                    .resultStatusCode(STATUS_RECORDED)
                    // 클라이언트 시계를 신뢰하지 않는다. 입력 시각의 기준은 서버 하나여야 한다.
                    .recordedAt(LocalDateTime.now())
                    .recordedById(request.getRecordedById())
                    .build();
            labResult.assignLabOrderItem(labOrderItem);

        } else {
            // 결과항목 방식 — 헤더의 resultValue/resultUnit/referenceRange 는 전부 NULL 로 둔다.
            List<LabResultDetailEntity> detailEntities =
                    buildValidatedDetails(request.getDetails(), itemRules, labOrderItem.getLabOrder().getPatientId());

            labResult = LabResultEntity.builder()
                    .resultValue(null)
                    .resultUnit(null)
                    .referenceRange(null)
                    .abnormalYn(aggregateAbnormalYn(detailEntities))
                    .resultStatusCode(STATUS_RECORDED)
                    .recordedAt(LocalDateTime.now())
                    .recordedById(request.getRecordedById())
                    .build();
            labResult.assignLabOrderItem(labOrderItem);
            labResult.replaceDetails(detailEntities);
        }

        LabResultEntity saved = labResultRepository.save(labResult);
        return toResponseWithDetails(saved);
    }

    /**
     * 적합성 판정이 끝난 접수가 있는지 확인한다. 없으면 LAB066. (후속조치 #10, UC-RST-01)
     *
     * ⚠ 판정 규칙은 SpecimenReadiness 에 있다. 워크리스트가 RESULT 단계를 표시하는 기준과 같다.
     *   예전에는 화면(워크리스트)만 이 조건을 보고 서버는 보지 않아서, API 를 직접 부르면
     *   판정 전 검체로도 결과가 등록됐다.
     *
     * ⚠ 결과는 오더(검사항목)에 붙고 검체는 접수에 붙는다. LAB_ORDER : LAB_RECEPTION = 1:N 이라
     *   처리 대상(ACCEPTED) 접수 중 하나라도 준비됐으면 통과로 본다. (제외된 접수의 검체는 보지 않는다)
     *
     * ⚠ 쿼리는 접수 수와 무관하게 2번이다(검체 1 + 판정 1). 워크리스트와 같은 IN 절 방식.
     */
    private void assertSpecimenReady(LabOrderEntity labOrder) {
        List<String> receptionIds = labReceptionRepository
                .findByLabOrder_LabOrderIdAndReceptionStatusCodeOrderByCreatedAtDesc(
                        labOrder.getLabOrderId(), ReceptionStatus.ACCEPTED.name())
                .stream()
                .map(LabReceptionEntity::getLabReceptionId)
                .toList();

        if (!receptionIds.isEmpty()) {
            Map<String, List<SpecimenEntity>> specimensByReceptionId = specimenRepository
                    .findByLabReception_LabReceptionIdIn(receptionIds).stream()
                    .collect(Collectors.groupingBy(s -> s.getLabReception().getLabReceptionId()));

            List<String> specimenIds = specimensByReceptionId.values().stream()
                    .flatMap(List::stream)
                    .map(SpecimenEntity::getSpecimenId)
                    .toList();

            Map<String, SpecimenAcceptanceEntity> acceptanceBySpecimenId = specimenIds.isEmpty()
                    ? Map.of()
                    : specimenAcceptanceRepository.findBySpecimen_SpecimenIdIn(specimenIds).stream()
                            .collect(Collectors.toMap(a -> a.getSpecimen().getSpecimenId(), a -> a));

            boolean anyReady = receptionIds.stream().anyMatch(receptionId -> {
                List<SpecimenEntity> specimens = specimensByReceptionId.getOrDefault(receptionId, List.of());
                List<SpecimenAcceptanceEntity> acceptances = specimens.stream()
                        .map(s -> acceptanceBySpecimenId.get(s.getSpecimenId()))
                        .filter(Objects::nonNull)
                        .toList();
                long recollectionCount = acceptances.stream()
                        .filter(a -> YES.equals(a.getRecollectionRequestedYn()))
                        .count();
                return SpecimenReadiness.isReadyForResult(
                        specimens.size(), acceptances.size(), recollectionCount);
            });

            if (anyReady) {
                return;
            }
        }

        throw new LabImagingBusinessException(
                LabMessageCode.LAB066,
                "적합성 판정이 끝나지 않아 결과를 등록할 수 없습니다. (labOrderId=" + labOrder.getLabOrderId() + ")");
    }

    // ------------------------------------------------------------------
    // ZP2-101 상태 전이 관리 (수정 / 확정)
    // ------------------------------------------------------------------

    /**
     * 확정 전 결과를 수정한다.
     *
     * ⚠ 확정된 결과는 수정할 수 없다. 확정은 "이 값으로 판독을 마쳤다"는 선언이라
     *   그 뒤에 값이 바뀌면 확정 자체가 의미를 잃는다.
     *
     * ⚠ 참고범위가 함께 바뀔 수 있으므로 abnormalYn 을 다시 계산한다.
     *   값만 고치고 판정을 그대로 두면 "범위 안인데 비정상"인 행이 남는다.
     *
     * ⚠ 6차: 결과항목 방식이면 details 를 통째로 교체(replaceDetails)하고 헤더 abnormal_yn 을
     *   다시 집계한다. 기존 방식이면 지금까지와 동일하게 헤더 값만 고친다. 어느 방식인지는
     *   등록 시점과 같은 기준(이 검사에 결과항목 규칙이 있는가)으로 판단한다 — 등록 후에
     *   admin 에서 규칙을 새로 추가/삭제해도 "이 결과가 어느 방식으로 등록됐는지"가 바뀌면
     *   안 되지만, 그 경합은 이번 범위에서 다루지 않는다(운영 중 마스터 변경은 드묾).
     */
    @Transactional
    public LabResultSummaryDto updateLabResult(String labResultId, LabResultUpdateRequestDto request) {

        LabResultEntity labResult = findResultOrThrow(labResultId);

        if (STATUS_CONFIRMED.equals(labResult.getResultStatusCode())) {
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB040,
                    "이미 확정된 결과는 수정할 수 없습니다. (labResultId=" + labResultId + ")"
            );
        }

        LabOrderItemEntity labOrderItem = labResult.getLabOrderItem();
        List<LabResultItemRuleEntity> itemRules =
                labResultItemRuleRepository.findByTestTypeCodeAndUseYnOrderByItemSeqAsc(
                        labOrderItem.getLabItemCode(), YES);

        if (itemRules.isEmpty()) {
            rejectIfDetailsSentToUnsupportedTest(request.getDetails(), labOrderItem.getLabItemCode());
            requireResultValue(request.getResultValue());
            validateNumericResultIfApplicable(request.getResultValue(), request.getReferenceRange(), "resultValue");

            labResult.modifyResult(
                    request.getResultValue(),
                    request.getResultUnit(),
                    request.getReferenceRange(),
                    AbnormalYnDecider.decide(request.getResultValue(), request.getReferenceRange()));
        } else {
            List<LabResultDetailEntity> detailEntities =
                    buildValidatedDetails(request.getDetails(), itemRules, labOrderItem.getLabOrder().getPatientId());
            labResult.replaceDetails(detailEntities);
            labResult.updateAbnormalYn(aggregateAbnormalYn(detailEntities));
        }

        // 영속 상태라 flush 시점에 반영된다. save 를 다시 부를 필요가 없다.
        return toResponseWithDetails(labResult);
    }

    /**
     * 결과를 확정한다. 등록(01) → 확정(02).
     *
     * ⚠ 이미 확정된 건은 다시 확정하지 않는다. 허용하면 confirmed_at 이 덮어써져
     *   "언제 확정했는가"가 마지막 호출 시각으로 바뀐다.
     *
     * ⚠ confirmedById 도 참조 식별자다. 직원 서비스에 존재 여부를 묻지 않는다.
     *   (recordedById 와 같은 취급 — LabResultCreateRequestDto 주석 참고)
     */
    @Transactional
    public LabResultSummaryDto confirmLabResult(String labResultId, String confirmedById) {

        LabResultEntity labResult = findResultOrThrow(labResultId);

        if (STATUS_CONFIRMED.equals(labResult.getResultStatusCode())) {
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB041,
                    "이미 확정된 결과입니다. (확정일시=" + labResult.getConfirmedAt() + ")"
            );
        }

        labResult.getLabOrderItem().requireNotCancelled();

        validateCode(RESULT_STATUS_CD, STATUS_CONFIRMED, "결과상태코드");

        // D3 — 입력자 본인 확정 금지. 시연 환경이 1인이라 기본은 꺼 두고 설정으로만 켤 수 있게 한다.
        if (forbidSelfConfirm && confirmedById != null && confirmedById.equals(labResult.getRecordedById())) {
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB068,
                    "결과 입력자와 확정자가 같습니다. 다른 담당자가 확정해야 합니다. (labResultId=" + labResultId + ")");
        }

        labResult.confirm(STATUS_CONFIRMED, confirmedById, LocalDateTime.now());

        // 청구 요청 — 같은 트랜잭션에 발신 이력(01)으로 기록되고, 커밋된 뒤에만 발행된다(D8). 실패해도 확정은 성공한다.
        billingChargeService.requestLabCharge(labResult.getLabOrderItem());
        // 결과 전송 (결과 1건 = 이벤트 1건, D10). 청구와 같이 이력에 남고 커밋 후 발행된다. 실패해도 확정은 성공한다.
        labResultTransmissionService.transmitGeneral(labResult);

        return toResponseWithDetails(labResult);
    }

    // ------------------------------------------------------------------
    // 조회
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public LabResultSummaryDto getLabResultById(String labResultId) {
        return toResponseWithDetails(findResultOrThrow(labResultId));
    }

    /**
     * 접수 1건의 검사항목 목록을 결과와 함께 조회한다. (결과 등록 화면용, ZP2-104)
     *
     * ⚠ 결과가 없는 항목도 빠뜨리지 않고 담는다. 등록 대상이 바로 그 항목들이다.
     *   결과 테이블에서 시작해 조회하면 아직 등록되지 않은 항목이 목록에서 사라진다.
     *   그래서 검사항목을 기준으로 뽑고, 결과를 붙이는 방향으로 조립한다.
     *
     * ⚠ 결과는 항목ID 목록으로 한 번에 조회해 메모리에서 붙인다.
     *   항목마다 결과를 조회하면 항목 수만큼 쿼리가 나간다(N+1).
     *   (LabWorklistService 의 IN 절 조립, SpecimenService.findFitnessStatus 와 같은 방식)
     *
     * ⚠ 6차: entryItems(결과항목 입력 양식)를 함께 담는다(2-4). 이 접수의 검사항목코드들에 대한
     *   결과항목 규칙·참고범위·환자 성별을 전부 배치로 한 번씩만 조회한다 — "환자 성별 조회는
     *   접수당 1회만"(항목마다 부르지 않는다). 결과항목 규칙이 하나도 없는 접수면 성별 조회 자체를
     *   생략한다(불필요한 외부 호출을 만들지 않는다).
     */
    @Transactional(readOnly = true)
    public List<LabResultItemDto> getResultItemsByReceptionNo(String receptionNo) {

        List<LabOrderItemEntity> items = labOrderItemRepository.findByReceptionNo(receptionNo);
        if (items.isEmpty()) {
            // 접수는 있는데 항목이 없는 경우다. 빈 목록이 정상이므로 예외를 던지지 않는다.
            return List.of();
        }

        List<String> itemIds = items.stream()
                .map(LabOrderItemEntity::getLabOrderItemId)
                .toList();

        List<LabResultEntity> results = labResultRepository.findByLabOrderItem_LabOrderItemIdIn(itemIds);
        Map<String, LabResultEntity> resultByItemId = results.stream()
                .collect(Collectors.toMap(
                        result -> result.getLabOrderItem().getLabOrderItemId(),
                        result -> result));

        List<String> resultIds = results.stream().map(LabResultEntity::getLabResultId).toList();
        Map<String, List<LabResultDetailEntity>> detailsByResultId = resultIds.isEmpty()
                ? Map.of()
                : labResultDetailRepository.findByLabResult_LabResultIdInOrderByDetailSeqAsc(resultIds).stream()
                        .collect(Collectors.groupingBy(d -> d.getLabResult().getLabResultId()));

        // 결과항목 규칙 — 이 접수의 검사항목코드들 전체를 한 번에 조회
        List<String> testTypeCodes = items.stream()
                .map(LabOrderItemEntity::getLabItemCode)
                .distinct()
                .toList();
        List<LabResultItemRuleEntity> allRules = labResultItemRuleRepository
                .findByTestTypeCodeInAndUseYnOrderByTestTypeCodeAscItemSeqAsc(testTypeCodes, YES);
        Map<String, List<LabResultItemRuleEntity>> rulesByTestType = allRules.stream()
                .collect(Collectors.groupingBy(LabResultItemRuleEntity::getTestTypeCode));

        // 결과항목 규칙이 있는 접수만 환자 성별을 조회한다 — 없으면 참고범위를 적용할 대상 자체가 없다.
        String genderCode = allRules.isEmpty()
                ? null
                : patientServiceBusinessDelegate.findGenderCode(items.get(0).getLabOrder().getPatientId());

        List<String> allResultItemCodes = allRules.stream()
                .map(LabResultItemRuleEntity::getResultItemCode)
                .distinct()
                .toList();
        Map<String, String> referenceRangeByCode = resolveReferenceRanges(allResultItemCodes, genderCode);

        return items.stream()
                .map(item -> {
                    LabResultEntity result = resultByItemId.get(item.getLabOrderItemId());
                    LabResultSummaryDto resultDto = (result == null) ? null : labResultMapper.toResponse(result)
                            .toBuilder()
                            .details(labResultMapper.toDetailResponseList(
                                    detailsByResultId.getOrDefault(result.getLabResultId(), List.of())))
                            .build();

                    List<LabResultEntryItemDto> entryItems = rulesByTestType
                            .getOrDefault(item.getLabItemCode(), List.of()).stream()
                            .map(rule -> LabResultEntryItemDto.builder()
                                    .resultItemCode(rule.getResultItemCode())
                                    .itemSeq(rule.getItemSeq())
                                    .defaultUnit(rule.getDefaultUnit())
                                    .referenceRange(referenceRangeByCode.get(rule.getResultItemCode()))
                                    .build())
                            .toList();

                    return LabResultItemDto.builder()
                            .labOrderItemId(item.getLabOrderItemId())
                            .labItemCode(item.getLabItemCode())
                            .resultType(labResultTypeResolver.resolve(item.getLabItemCode()).name())
                            // 결과가 없는 항목은 null 로 둔다. 화면이 "미등록"으로 읽는다.
                            .result(resultDto)
                            .entryItems(entryItems)
                            .build();
                })
                .toList();
    }

    /**
     * 검사항목ID로 결과를 조회한다.
     * 결과 화면이 "이 항목에 결과가 있나"를 물을 때 쓴다. 검사항목 1건에 결과 1건이라 단건이다.
     */
    @Transactional(readOnly = true)
    public LabResultSummaryDto getLabResultByLabOrderItemId(String labOrderItemId) {
        LabResultEntity labResult = labResultRepository
                .findByLabOrderItem_LabOrderItemId(labOrderItemId)
                .orElseThrow(() -> new LabImagingBusinessException(
                        LabMessageCode.LAB037,
                        "등록된 검사 결과를 찾을 수 없습니다. (labOrderItemId=" + labOrderItemId + ")"
                ));
        return toResponseWithDetails(labResult);
    }

    // ------------------------------------------------------------------
    // 결과항목(상세) — 6차, 2-2
    // ------------------------------------------------------------------

    /** 기존 방식(규칙 없는 검사)인데 details 가 왔으면 거절한다. */
    private void rejectIfDetailsSentToUnsupportedTest(List<LabResultDetailRequestDto> details, String labItemCode) {
        if (details != null && !details.isEmpty()) {
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB099,
                    "이 검사는 결과항목을 지원하지 않습니다. (검사항목코드=" + labItemCode + ")");
        }
    }

    /** 기존 방식은 resultValue 가 필수다. 요청 DTO 의 @NotBlank 를 뗐으므로(조건부 필수) 서비스가 확인한다. */
    private void requireResultValue(String resultValue) {
        if (resultValue == null || resultValue.isBlank()) {
            throw new LabImagingBusinessException(LabMessageCode.LAB998, "resultValue 는 필수입니다.");
        }
    }

    /**
     * 참고범위가 수치 범위("3.5-5.5")일 때만 적용되는 검증 두 가지. (LAB105/106, 04번 지시서 Phase 3-A)
     *   1) 범위 자체가 뒤집혀 있으면(하한&gt;상한) LAB106 — AbnormalYnDecider.decide() 가
     *      그 범위로는 거의 모든 값을 비정상으로 잘못 판정하게 된다. 하한==상한은 허용한다
     *      (지시서 §3-A: "하한 ≤ 상한이어야 한다").
     *   2) 범위는 멀쩡한데 결과값이 엄격한 십진수로 안 읽히면 LAB105 — decide() 는 느슨한 파싱
     *      (Double.valueOf)으로 "1e3"·"NaN"·"Infinity" 까지 숫자로 읽어버리고, 그 밖의 오타
     *      ("4.2mg", "4,2")는 조용히 정성 비교로 내려가(AbnormalYnDecider 클래스 주석 참고)
     *      참고범위 문자열과 같을 수 없어 항상 비정상으로 잘못 판정되는데도 입력한 사람은 그
     *      사실을 알 길이 없다.
     *
     * ⚠ 참고범위가 수치 범위가 아니면(정성, 또는 "≤5" 같은 미지원 표기) 아무것도 하지 않는다 —
     *   그 경우의 한계는 AbnormalYnDecider 가 이미 문서화한 그대로 둔다.
     */
    private void validateNumericResultIfApplicable(String resultValue, String referenceRange, String fieldLabel) {
        if (!AbnormalYnDecider.isNumericRange(referenceRange)) {
            return;
        }
        if (!AbnormalYnDecider.isValidNumericRangeOrder(referenceRange)) {
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB106,
                    "참고범위의 하한이 상한보다 큽니다. (" + fieldLabel + " 참고범위=" + referenceRange + ")");
        }
        if (resultValue != null && !resultValue.isBlank() && !AbnormalYnDecider.isNumeric(resultValue)) {
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB105,
                    "결과값이 숫자 형식이 아닙니다. 참고범위(" + referenceRange + ")가 수치 범위입니다. ("
                            + fieldLabel + "=" + resultValue + ")");
        }
    }

    /**
     * 요청 details 를 검증하고 저장할 LabResultDetailEntity 목록을 만든다. (2-2 검증 6종)
     *
     * 검증 순서: 개수(1~4) → 중복 → 이 검사의 규칙에 속하는지 → 공통코드(RESULT_ITEM_CD).
     * 참고범위·단위는 서버가 정한다 — 요청 DTO 에는 그 필드 자체가 없다.
     */
    private List<LabResultDetailEntity> buildValidatedDetails(List<LabResultDetailRequestDto> details,
                                                              List<LabResultItemRuleEntity> itemRules,
                                                              String patientId) {
        if (details == null || details.isEmpty()) {
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB102, "이 검사는 결과항목 입력이 필요합니다. (최소 1개)");
        }
        if (details.size() > MAX_DETAILS) {
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB101, "결과항목 개수가 허용 범위를 벗어났습니다. (입력=" + details.size()
                            + "개, 허용=1~" + MAX_DETAILS + "개)");
        }

        Map<String, LabResultItemRuleEntity> ruleByCode = itemRules.stream()
                .collect(Collectors.toMap(LabResultItemRuleEntity::getResultItemCode, rule -> rule));

        Set<String> seen = new HashSet<>();
        for (LabResultDetailRequestDto detail : details) {
            if (!seen.add(detail.getResultItemCode())) {
                throw new LabImagingBusinessException(
                        LabMessageCode.LAB100,
                        "같은 결과항목이 중복 입력되었습니다. (결과항목코드=" + detail.getResultItemCode() + ")");
            }
            if (!ruleByCode.containsKey(detail.getResultItemCode())) {
                throw new LabImagingBusinessException(
                        LabMessageCode.LAB099,
                        "이 검사에 속하지 않는 결과항목입니다. (결과항목코드=" + detail.getResultItemCode() + ")");
            }
            validateCode(RESULT_ITEM_CD, detail.getResultItemCode(), "결과항목코드");
        }

        // 환자 성별은 이 결과 1건 등록에 필요한 만큼만 1회 조회한다(접수 목록 조회는 별도로 1회 — getResultItemsByReceptionNo).
        String genderCode = patientServiceBusinessDelegate.findGenderCode(patientId);
        List<String> resultItemCodes = details.stream().map(LabResultDetailRequestDto::getResultItemCode).toList();
        Map<String, String> referenceRangeByCode = resolveReferenceRanges(resultItemCodes, genderCode);

        List<LabResultDetailEntity> entities = new ArrayList<>();
        for (LabResultDetailRequestDto detail : details) {
            LabResultItemRuleEntity rule = ruleByCode.get(detail.getResultItemCode());
            String referenceRange = referenceRangeByCode.get(detail.getResultItemCode());
            validateNumericResultIfApplicable(
                    detail.getResultValue(), referenceRange, "resultItemCode=" + detail.getResultItemCode());
            // 참고범위 기준이 없으면(환자 성별 미상 + ALL 행도 없음) 판정하지 않는다 — 2-2.
            String abnormalYn = (referenceRange == null) ? NO
                    : AbnormalYnDecider.decide(detail.getResultValue(), referenceRange);

            entities.add(LabResultDetailEntity.builder()
                    .detailSeq(rule.getItemSeq())
                    .resultItemCode(detail.getResultItemCode())
                    .resultValue(detail.getResultValue())
                    .resultUnit(rule.getDefaultUnit())
                    .referenceRange(referenceRange)
                    .abnormalYn(abnormalYn)
                    .build());
        }
        return entities;
    }

    /** 헤더 abnormal_yn — 상세 중 하나라도 이상(Y)이면 Y. (2-2) */
    private String aggregateAbnormalYn(List<LabResultDetailEntity> details) {
        return details.stream().anyMatch(d -> YES.equals(d.getAbnormalYn())) ? YES : NO;
    }

    /**
     * 결과항목코드별 참고범위를 적용 순서(환자 성별 → ALL → 없음)대로 정리한다. 쿼리 1번으로 끝낸다.
     * ⚠ 성별을 모르거나(genderCode=null, 즉 03 미상·04 기타·조회 실패) 그 항목에 ALL 행이 없으면
     *   맵에 키가 없다 — 호출한 쪽은 Map.get() 의 null 을 "판정 보류"로 읽는다(2-2).
     */
    private Map<String, String> resolveReferenceRanges(List<String> resultItemCodes, String genderCode) {
        if (resultItemCodes.isEmpty()) {
            return Map.of();
        }
        List<LabReferenceRangeEntity> ranges =
                labReferenceRangeRepository.findByResultItemCodeInAndUseYn(resultItemCodes, YES);

        Map<String, String> resolved = new HashMap<>();
        // 1순위: ALL 을 먼저 채우고
        for (LabReferenceRangeEntity range : ranges) {
            if (SEX_ALL.equals(range.getSexCode())) {
                resolved.put(range.getResultItemCode(), range.getReferenceRange());
            }
        }
        // 2순위: 환자 성별에 맞는 행이 있으면 덮어쓴다 — 성별 특이값이 ALL 보다 우선한다.
        if (genderCode != null) {
            for (LabReferenceRangeEntity range : ranges) {
                if (genderCode.equals(range.getSexCode())) {
                    resolved.put(range.getResultItemCode(), range.getReferenceRange());
                }
            }
        }
        return resolved;
    }

    /** 저장된 결과를 응답 DTO 로 바꾸며 details 를 채운다. (LabResultMapper 가 details 를 자동 매핑하지 않는 이유는 매퍼 주석 참고) */
    private LabResultSummaryDto toResponseWithDetails(LabResultEntity labResult) {
        return labResultMapper.toResponse(labResult).toBuilder()
                .details(labResultMapper.toDetailResponseList(labResult.getDetails()))
                .build();
    }

    // ------------------------------------------------------------------
    // 공통
    // ------------------------------------------------------------------

    private LabResultEntity findResultOrThrow(String labResultId) {
        return labResultRepository.findById(labResultId)
                .orElseThrow(() -> new LabImagingBusinessException(
                        LabMessageCode.LAB037,
                        "등록된 검사 결과를 찾을 수 없습니다. (labResultId=" + labResultId + ")"
                ));
    }

    /** LabOrderService.validateCode 와 같은 패턴. 캐시 적재 관련 주의도 그쪽 주석 참고. */
    private void validateCode(String groupCode, String code, String fieldLabel) {
        if (!commonCodeCache.isValid(groupCode, code)) {
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB017,
                    "유효하지 않은 " + fieldLabel + "입니다. (" + groupCode + "=" + code + ")"
            );
        }
    }
}
