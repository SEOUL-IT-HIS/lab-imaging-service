package kr.co.seoulit.his.labimagingservice.laborder.service;

import kr.co.seoulit.his.labimagingservice.common.status.OrderItemStatus;
import kr.co.seoulit.his.labimagingservice.common.status.ReceptionStatus;
import kr.co.seoulit.his.labimagingservice.laborder.dto.LabWorklistItemDto;
import kr.co.seoulit.his.labimagingservice.laborder.dto.WorklistStep;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabReceptionEntity;
import kr.co.seoulit.his.labimagingservice.laborder.mapper.LabWorklistMapper;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabOrderItemEntity;
import kr.co.seoulit.his.labimagingservice.laborder.repository.LabOrderItemRepository;
import kr.co.seoulit.his.labimagingservice.laborder.repository.LabReceptionRepository;
import kr.co.seoulit.his.labimagingservice.labresult.entity.LabResultEntity;
import kr.co.seoulit.his.labimagingservice.labresult.microbiology.entity.MicrobiologyResultEntity;
import kr.co.seoulit.his.labimagingservice.labresult.microbiology.repository.MicrobiologyResultRepository;
import kr.co.seoulit.his.labimagingservice.labresult.pathology.entity.PathologyResultEntity;
import kr.co.seoulit.his.labimagingservice.labresult.pathology.repository.PathologyResultRepository;
import kr.co.seoulit.his.labimagingservice.labresult.type.LabResultTypeResolver;
import kr.co.seoulit.his.labimagingservice.labresult.repository.LabResultRepository;
import kr.co.seoulit.his.labimagingservice.labschedule.entity.LabScheduleEntity;
import kr.co.seoulit.his.labimagingservice.labschedule.repository.LabScheduleRepository;
import kr.co.seoulit.his.labimagingservice.labspecimen.entity.SpecimenAcceptanceEntity;
import kr.co.seoulit.his.labimagingservice.labspecimen.entity.SpecimenEntity;
import kr.co.seoulit.his.labimagingservice.labspecimen.repository.SpecimenAcceptanceRepository;
import kr.co.seoulit.his.labimagingservice.labspecimen.repository.SpecimenRepository;
import kr.co.seoulit.his.labimagingservice.labspecimen.service.SpecimenReadiness;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 검사 워크리스트 조회 서비스.
 *
 * ⚠ LabOrderService 가 아니라 별도 서비스로 둔 이유 —
 *   워크리스트는 접수 하나만 보는 게 아니라 일정·검체·판정을 가로질러 "어디까지 진행됐는지"를
 *   조립하는 조회다. 접수 등록/조회를 담당하는 LabOrderService 에 넣으면 그 클래스가
 *   다른 도메인 리포지토리를 4개나 들고 있게 된다.
 *
 * ⚠ 쿼리 수는 접수가 몇 건이든 항상 3번이다. (접수 1 + 일정 1 + 검체 1 + 판정 1)
 *   행마다 조회하면 N+1 이 되므로, 목록을 먼저 뽑고 ID 를 모아 IN 절로 일괄 조회한 뒤
 *   메모리에서 붙인다. (LabOrderService.findLatestScheduledAt 와 같은 방식)
 *
 * ⚠ left join 한 방으로 가져오지 않는 이유 —
 *   SPECIMEN 은 접수와 1:N 이라 검체 3건인 접수가 결과에서 3행으로 늘어난다.
 *   group by 집계로 눌러야 하는데, 그러면 "검체 3건 중 2건 판정" 같은 값을 만들기가 오히려 번거롭다.
 */
@Service
@RequiredArgsConstructor
public class LabWorklistService {

    private static final String YES = "Y";
    private static final String NO = "N";

    /** 결과상태 확정(02). LabResultService 의 STATUS_CONFIRMED 와 같은 값이다. */
    private static final String RESULT_STATUS_CONFIRMED = "02";

    private final LabReceptionRepository labReceptionRepository;
    private final LabScheduleRepository labScheduleRepository;
    private final SpecimenRepository specimenRepository;
    private final SpecimenAcceptanceRepository specimenAcceptanceRepository;
    private final LabOrderItemRepository labOrderItemRepository;
    private final LabResultRepository labResultRepository;
    private final MicrobiologyResultRepository microbiologyResultRepository;
    private final PathologyResultRepository pathologyResultRepository;
    private final LabResultTypeResolver labResultTypeResolver;
    private final LabWorklistMapper labWorklistMapper;

    /**
     * 워크리스트 조회.
     *
     * @param receptionStatusCode "ACCEPTED"=처리 대상, "EXCLUDED"=제외됨, "CANCELLED"=취소됨, null=전체
     */
    @Transactional(readOnly = true)
    public List<LabWorklistItemDto> getWorklist(String receptionStatusCode) {
        List<LabReceptionEntity> receptions = findReceptionsBy(receptionStatusCode);
        if (receptions.isEmpty()) {
            return List.of();
        }
        // ⚠ Xxx::method 는 메서드 참조다. r -> r.getLabReceptionId() 의 축약형이고,
        //   스트림이 흘려보내는 요소 하나하나가 그 메서드의 수신자가 된다.
        List<String> receptionIds = receptions.stream()
                .map(LabReceptionEntity::getLabReceptionId)
                .toList();

        Map<String, LocalDateTime> scheduledAtByReceptionId = findScheduledAt(receptionIds);
        Map<String, List<SpecimenEntity>> specimensByReceptionId = findSpecimens(receptionIds);
        Map<String, SpecimenAcceptanceEntity> acceptanceBySpecimenId =
                findAcceptances(specimensByReceptionId);

        /*
         * 결과는 접수가 아니라 오더에 매달린다. (LAB_ORDER → LAB_ORDER_ITEM → LAB_RESULT)
         * LAB_ORDER : LAB_RECEPTION = 1:N 이라 한 오더의 접수가 여럿이면 항목·결과를 공유한다.
         * 그래서 오더ID 로 모아 두 번 조회하고 접수마다 같은 값을 붙인다.
         */
        List<String> orderIds = receptions.stream()
                .map(reception -> reception.getLabOrder().getLabOrderId())
                .distinct()
                .toList();

        Map<String, List<LabOrderItemEntity>> itemsByOrderId = findOrderItems(orderIds);
        Map<String, LabResultEntity> resultByItemId = findResults(itemsByOrderId);
        // 미생물 결과는 검체(→접수) 단위다. 접수당 1건 제약(5차)이라 접수ID 로 모은다. (N+1 방지 IN 절 1회)
        Map<String, String> microStatusByReceptionId = findMicrobiologyStatus(receptionIds);
        // 병리 결과는 검사항목 1:1 이라 항목ID 로 모은다. (N+1 방지 IN 절 1회)
        Map<String, String> pathologyStatusByItemId = findPathologyStatus(itemsByOrderId);

        return receptions.stream()
                .map(reception -> toItem(
                        reception,
                        scheduledAtByReceptionId.get(reception.getLabReceptionId()),
                        specimensByReceptionId.getOrDefault(reception.getLabReceptionId(), List.of()),
                        acceptanceBySpecimenId,
                        itemsByOrderId.getOrDefault(reception.getLabOrder().getLabOrderId(), List.of()),
                        resultByItemId,
                        microStatusByReceptionId.get(reception.getLabReceptionId()),
                        pathologyStatusByItemId))
                .toList();
    }

    /**
     * 접수ID → 미생물 결과의 결과상태(01/02). 결과가 없는 접수는 키가 없다. (5차 Phase 3)
     *
     * ⚠ 미생물 결과는 검체에 붙는다. "접수당 미생물 항목 1개 + 결과 1건" 제약이라, 접수의 미생물 항목에
     *   이 상태를 그대로 대응시킨다(toItem 참고).
     */
    /** 검사항목ID → 병리 결과의 결과상태. 결과가 없는 항목은 키가 없다. (5차 Phase 4) */
    private Map<String, String> findPathologyStatus(Map<String, List<LabOrderItemEntity>> itemsByOrderId) {
        List<String> itemIds = itemsByOrderId.values().stream()
                .flatMap(List::stream)
                .map(LabOrderItemEntity::getLabOrderItemId)
                .toList();
        if (itemIds.isEmpty()) {
            return Map.of();
        }
        return pathologyResultRepository.findByItemIds(itemIds).stream()
                .collect(Collectors.toMap(
                        r -> r.getLabOrderItem().getLabOrderItemId(),
                        PathologyResultEntity::getResultStatusCode));
    }

    private Map<String, String> findMicrobiologyStatus(List<String> receptionIds) {
        return microbiologyResultRepository.findByReceptionIds(receptionIds).stream()
                .collect(Collectors.toMap(
                        r -> r.getSpecimen().getLabReception().getLabReceptionId(),
                        MicrobiologyResultEntity::getResultStatusCode,
                        // 제약상 한 건이어야 하지만, 제약 전 데이터가 있어도 목록 조회가 터지지 않게 한다.
                        (a, b) -> a));
    }

    /**
     * 접수상태 필터에 따라 조회 메서드를 고른다. 값이 없거나 모르는 값이면 전체.
     * ⚠ CANCELLED 추가 (05번 지시서 Phase 4) — findWorklistByStatus 는 상태값을 그대로 받는
     *   범용 조회라 새 상태를 추가해도 쿼리를 새로 만들 필요가 없다.
     */
    private List<LabReceptionEntity> findReceptionsBy(String receptionStatusCode) {
        if (ReceptionStatus.ACCEPTED.name().equals(receptionStatusCode)
                || ReceptionStatus.EXCLUDED.name().equals(receptionStatusCode)
                || ReceptionStatus.CANCELLED.name().equals(receptionStatusCode)) {
            return labReceptionRepository.findWorklistByStatus(receptionStatusCode);
        }
        return labReceptionRepository.findWorklistAll();
    }

    /** 접수ID → 최종 일정의 예정일시. 일정이 없는 접수는 키가 없다. */
    private Map<String, LocalDateTime> findScheduledAt(List<String> receptionIds) {
        return labScheduleRepository
                .findByLabReception_LabReceptionIdInAndLatestYn(receptionIds, YES).stream()
                .collect(Collectors.toMap(
                        // getLabReception() 을 거쳐 두 단계로 들어가야 해서 :: 로 못 줄인다
                        schedule -> schedule.getLabReception().getLabReceptionId(),
                        // 한 번만 부르면 되니 메서드 참조로 줄인다
                        LabScheduleEntity::getScheduledAt));
    }

    /** 접수ID → 그 접수의 검체 목록. 검체가 없는 접수는 키가 없다. */
    private Map<String, List<SpecimenEntity>> findSpecimens(List<String> receptionIds) {
        return specimenRepository.findByLabReception_LabReceptionIdIn(receptionIds).stream()
                .collect(Collectors.groupingBy(
                        specimen -> specimen.getLabReception().getLabReceptionId()));
    }

    /** 검체ID → 적합성 판정. 판정이 없는 검체는 키가 없다. */
    private Map<String, SpecimenAcceptanceEntity> findAcceptances(
            Map<String, List<SpecimenEntity>> specimensByReceptionId) {

        List<String> specimenIds = specimensByReceptionId.values().stream()
                .flatMap(List::stream)
                .map(SpecimenEntity::getSpecimenId)
                .toList();

        if (specimenIds.isEmpty()) {
            return Map.of();
        }
        return specimenAcceptanceRepository.findBySpecimen_SpecimenIdIn(specimenIds).stream()
                .collect(Collectors.toMap(
                        acceptance -> acceptance.getSpecimen().getSpecimenId(),
                        acceptance -> acceptance));
    }

    /** 오더ID → 그 오더의 검사항목 목록. 항목이 없는 오더는 키가 없다. */
    private Map<String, List<LabOrderItemEntity>> findOrderItems(List<String> orderIds) {
        return labOrderItemRepository.findByLabOrderIdIn(orderIds).stream()
                .collect(Collectors.groupingBy(item -> item.getLabOrder().getLabOrderId()));
    }

    /** 검사항목ID → 결과. 결과가 없는 항목은 키가 없다. */
    private Map<String, LabResultEntity> findResults(
            Map<String, List<LabOrderItemEntity>> itemsByOrderId) {

        List<String> itemIds = itemsByOrderId.values().stream()
                .flatMap(List::stream)
                .map(LabOrderItemEntity::getLabOrderItemId)
                .toList();

        if (itemIds.isEmpty()) {
            return Map.of();
        }
        return labResultRepository.findByLabOrderItem_LabOrderItemIdIn(itemIds).stream()
                .collect(Collectors.toMap(
                        result -> result.getLabOrderItem().getLabOrderItemId(),
                        result -> result));
    }

    private LabWorklistItemDto toItem(LabReceptionEntity reception,
                                      LocalDateTime scheduledAt,
                                      List<SpecimenEntity> specimens,
                                      Map<String, SpecimenAcceptanceEntity> acceptanceBySpecimenId,
                                      List<LabOrderItemEntity> orderItems,
                                      Map<String, LabResultEntity> resultByItemId,
                                      String microbiologyStatus,
                                      Map<String, String> pathologyStatusByItemId) {

        int specimenCount = specimens.size();

        List<SpecimenAcceptanceEntity> acceptances = specimens.stream()
                .map(specimen -> acceptanceBySpecimenId.get(specimen.getSpecimenId()))
                .filter(acceptance -> acceptance != null)
                .toList();

        int judgedCount = acceptances.size();

        long recollectionCount = acceptances.stream()
                .filter(acceptance -> YES.equals(acceptance.getRecollectionRequestedYn()))
                .count();

        /*
         * 재채취 요청이 아직 "해소되지 않았는지" 판단한다.
         *
         * ⚠ 계산 규칙은 SpecimenReadiness 한 곳에 있다. 결과 등록 API(LabResultService.createLabResult)가
         *   같은 규칙으로 서버 검증을 하므로(LAB066), 여기서 조건을 따로 적으면 둘이 어긋난다.
         *   (2026-09-29 후속조치 #10 — 이전에는 이 계산이 여기에만 있었다)
         */
        boolean recollectionPending = SpecimenReadiness.isRecollectionPending(specimenCount, recollectionCount);

        /*
         * 취소된 항목은 진행도 분모에서 뺀다. (05번 지시서 Phase 4)
         * ⚠ 취소된 항목은 결과가 나올 일이 없다 — 그대로 두면 "2/5" 처럼 영원히 못 채우는
         *   분모가 남아 담당자가 착각한다.
         */
        List<LabOrderItemEntity> activeOrderItems = orderItems.stream()
                .filter(item -> !OrderItemStatus.CANCELLED.name().equals(item.getItemStatusCode()))
                .toList();

        int labItemCount = activeOrderItems.size();

        /*
         * 항목마다 "결과상태"를 모은다. 결과가 들어 있는 테이블이 항목 유형마다 다르다. (5차 D1)
         *   GENERAL      → LAB_RESULT (항목 1:1)
         *   MICROBIOLOGY → MICROBIOLOGY_RESULT (접수당 1건 → 접수의 미생물 항목에 대응)
         *   PATHOLOGY    → PATHOLOGY_RESULT (항목 1:1)
         * 그래야 진행도 n/m 의 m(항목 수)과 n(결과 수)이 같은 단위로 맞는다.
         */
        List<String> resultStatuses = activeOrderItems.stream()
                .map(item -> switch (labResultTypeResolver.resolve(item.getLabItemCode())) {
                    case GENERAL -> {
                        LabResultEntity general = resultByItemId.get(item.getLabOrderItemId());
                        yield general == null ? null : general.getResultStatusCode();
                    }
                    case MICROBIOLOGY -> microbiologyStatus;
                    case PATHOLOGY -> pathologyStatusByItemId.get(item.getLabOrderItemId());
                })
                .filter(status -> status != null)
                .toList();

        int resultCount = resultStatuses.size();
        int confirmedResultCount = (int) resultStatuses.stream()
                .filter(RESULT_STATUS_CONFIRMED::equals)
                .count();

        WorklistStep nextStep = decideNextStep(
                scheduledAt, specimenCount, judgedCount, recollectionPending);

        return labWorklistMapper.toWorklistItem(
                reception,
                scheduledAt,
                specimenCount,
                judgedCount,
                recollectionPending ? YES : NO,
                labItemCount,
                resultCount,
                confirmedResultCount,
                nextStep);
    }

    /**
     * 다음에 해야 할 일을 정한다. 위에서부터 먼저 걸리는 것이 답이다.
     *
     * ⚠ 이 순서가 곧 업무 순서다. 미판정 검체가 남아 있으면 재채취보다 판정이 먼저다.
     *   (판정을 해봐야 재채취가 필요한지 알 수 있다)
     *
     * ⚠ RESULT 가 마지막 단계다. 결과가 확정돼도 여기 머물고, 진행도는 항목 유형별(일반·미생물·병리)
     *   확정 수로 보여준다(5차 Phase 3). 판정까지 끝낸 건이 목록에 남는 이유를 담당자가 알 수 있다.
     */
    private WorklistStep decideNextStep(LocalDateTime scheduledAt,
                                        int specimenCount,
                                        int judgedCount,
                                        boolean recollectionPending) {
        if (scheduledAt == null) {
            return WorklistStep.SCHEDULE;
        }
        if (specimenCount == 0) {
            return WorklistStep.SPECIMEN;
        }
        if (judgedCount < specimenCount) {
            return WorklistStep.ACCEPTANCE;
        }
        if (recollectionPending) {
            return WorklistStep.RECOLLECT;
        }
        return WorklistStep.RESULT;
    }
}
