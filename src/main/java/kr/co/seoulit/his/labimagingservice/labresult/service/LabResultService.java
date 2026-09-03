package kr.co.seoulit.his.labimagingservice.labresult.service;

import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.cache.CommonCodeCache;
import kr.co.seoulit.his.labimagingservice.common.exception.LabImagingBusinessException;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabOrderItemEntity;
import kr.co.seoulit.his.labimagingservice.laborder.repository.LabOrderItemRepository;
import kr.co.seoulit.his.labimagingservice.labresult.dto.LabResultCreateRequestDto;
import kr.co.seoulit.his.labimagingservice.labresult.dto.LabResultItemDto;
import kr.co.seoulit.his.labimagingservice.labresult.dto.LabResultSummaryDto;
import kr.co.seoulit.his.labimagingservice.labresult.dto.LabResultUpdateRequestDto;
import kr.co.seoulit.his.labimagingservice.labresult.entity.LabResultEntity;
import kr.co.seoulit.his.labimagingservice.labresult.mapper.LabResultMapper;
import kr.co.seoulit.his.labimagingservice.labresult.repository.LabResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
 */
@Service
@RequiredArgsConstructor
public class LabResultService {

    /** 공통코드 그룹 — admin 에 01=등록, 02=확정으로 등록되어 있어야 한다. */
    private static final String RESULT_STATUS_CD = "RESULT_STATUS_CD";

    /** 결과상태: 등록(입력만 된 상태, 수정 가능) */
    private static final String STATUS_RECORDED = "01";
    /** 결과상태: 확정(더 이상 수정 불가) */
    private static final String STATUS_CONFIRMED = "02";

    private static final String YES = "Y";
    private static final String NO = "N";

    private final LabResultRepository labResultRepository;
    private final LabOrderItemRepository labOrderItemRepository;
    private final LabResultMapper labResultMapper;
    private final CommonCodeCache commonCodeCache;

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
     *   4) 참고범위로 정상/비정상 판정. (ZP2-99)
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

        if (labResultRepository.existsByLabOrderItem_LabOrderItemId(request.getLabOrderItemId())) {
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB036,
                    "이미 결과가 등록된 검사항목입니다. (검사항목코드=" + labOrderItem.getLabItemCode() + ")"
            );
        }

        validateCode(RESULT_STATUS_CD, STATUS_RECORDED, "결과상태코드");

        LabResultEntity labResult = LabResultEntity.builder()
                .resultValue(request.getResultValue())
                .resultUnit(request.getResultUnit())
                .referenceRange(request.getReferenceRange())
                .abnormalYn(decideAbnormalYn(request.getResultValue(), request.getReferenceRange()))
                .resultStatusCode(STATUS_RECORDED)
                // 클라이언트 시계를 신뢰하지 않는다. 입력 시각의 기준은 서버 하나여야 한다.
                .recordedAt(LocalDateTime.now())
                .recordedById(request.getRecordedById())
                .build();
        labResult.assignLabOrderItem(labOrderItem);

        return labResultMapper.toResponse(labResultRepository.save(labResult));
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

        labResult.modifyResult(
                request.getResultValue(),
                request.getResultUnit(),
                request.getReferenceRange(),
                decideAbnormalYn(request.getResultValue(), request.getReferenceRange()));

        // 영속 상태라 flush 시점에 반영된다. save 를 다시 부를 필요가 없다.
        return labResultMapper.toResponse(labResult);
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

        validateCode(RESULT_STATUS_CD, STATUS_CONFIRMED, "결과상태코드");

        labResult.confirm(STATUS_CONFIRMED, confirmedById, LocalDateTime.now());
        return labResultMapper.toResponse(labResult);
    }

    // ------------------------------------------------------------------
    // 조회
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public LabResultSummaryDto getLabResultById(String labResultId) {
        return labResultMapper.toResponse(findResultOrThrow(labResultId));
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

        Map<String, LabResultEntity> resultByItemId = labResultRepository
                .findByLabOrderItem_LabOrderItemIdIn(itemIds).stream()
                .collect(Collectors.toMap(
                        result -> result.getLabOrderItem().getLabOrderItemId(),
                        result -> result));

        return items.stream()
                .map(item -> {
                    LabResultEntity result = resultByItemId.get(item.getLabOrderItemId());
                    return LabResultItemDto.builder()
                            .labOrderItemId(item.getLabOrderItemId())
                            .labItemCode(item.getLabItemCode())
                            // 결과가 없는 항목은 null 로 둔다. 화면이 "미등록"으로 읽는다.
                            .result(result == null ? null : labResultMapper.toResponse(result))
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
        return labResultMapper.toResponse(labResult);
    }

    // ------------------------------------------------------------------
    // ZP2-99 기준값 및 참고범위 적용
    // ------------------------------------------------------------------

    /**
     * 참고범위와 결과값을 비교해 비정상 여부를 판정한다. (ZP2-99)
     *
     * ── 설계: 참고범위는 "정상으로 보는 값"이다
     *   정량("3.5-5.5")과 정성("음성")을 한 컬럼으로 다루기 위해 이렇게 정의했다.
     *   컬럼을 나누면 검사항목마다 어느 쪽을 쓰는지 판단해야 하는데, 그 구분 정보가 아직 없다.
     *
     * ── 판정 규칙
     *   1) 참고범위가 없으면          → N. 비교할 기준이 없다.
     *   2) "min-max" 이고 결과가 숫자 → 범위를 벗어나면 Y.
     *   3) 그 밖(정성값)             → 참고범위에 적힌 값과 다르면 Y.
     *                                  쉼표로 여러 정상값을 줄 수 있다. ("음성,정상")
     *
     * ⚠ 1번을 Y 가 아니라 N 으로 두는 이유 —
     *   기준이 없는 것과 비정상인 것은 다르다. Y 로 두면 참고범위를 안 적은 결과가 전부
     *   비정상으로 쌓여, 정작 진짜 비정상 건이 묻힌다.
     *   대신 판정하지 않았다는 사실이 화면에서 드러나야 한다 — 참고범위 칸이 비어 있는 것이 그 신호다.
     *
     * ⚠ 3번 덕분에 "양성"은 참고범위가 "음성"이면 자동으로 Y 가 된다.
     *   정성 결과를 무조건 N 으로 두면 양성 결과가 정상으로 분류되는데, 그건 위험하다.
     *
     * ⚠ 한계 — "≤5", "5 이하", "3.5~5.5" 같은 표기는 2번으로 인식하지 못해 3번(문자열 비교)으로
     *   내려가고, 그러면 대부분 Y 가 된다. 검사항목별 기준값 마스터가 생기면
     *   이 문자열 파싱 자체가 없어져야 한다. 그때까지의 임시 규칙이다.
     */
    private String decideAbnormalYn(String resultValue, String referenceRange) {

        if (referenceRange == null || referenceRange.isBlank()) {
            return NO;
        }

        Double min = parseRangeBound(referenceRange, 0);
        Double max = parseRangeBound(referenceRange, 1);
        Double value = parseNumber(resultValue);

        // 2) 정량 판정 — 범위와 결과값이 모두 숫자로 읽힐 때만 성립한다.
        if (min != null && max != null && value != null) {
            return (value < min || value > max) ? YES : NO;
        }

        // 3) 정성 판정 — 참고범위에 적힌 정상값 중 하나와 같으면 정상.
        return Arrays.stream(referenceRange.split(","))
                .map(String::trim)
                .anyMatch(normal -> normal.equalsIgnoreCase(resultValue.trim()))
                ? NO : YES;
    }

    /**
     * "3.5-5.5" 에서 index 번째 경계값을 꺼낸다. (0=하한, 1=상한)
     * 형식이 다르거나 숫자로 읽히지 않으면 null 을 돌려주고, 호출한 쪽이 정성 판정으로 넘어간다.
     *
     * ⚠ 음수 범위("-5--1")는 이 분리 방식으로 다룰 수 없다. 일반검사 수치에 음수가 없어 두고 간다.
     */
    private Double parseRangeBound(String referenceRange, int index) {
        String[] bounds = referenceRange.split("-");
        if (bounds.length != 2) {
            return null;
        }
        return parseNumber(bounds[index]);
    }

    /** 숫자로 읽히면 값을, 아니면 null. 정성값("음성")이 그대로 들어오므로 예외로 다루지 않는다. */
    private Double parseNumber(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Double.valueOf(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
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
