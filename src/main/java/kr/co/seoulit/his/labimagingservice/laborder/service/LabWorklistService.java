package kr.co.seoulit.his.labimagingservice.laborder.service;

import kr.co.seoulit.his.labimagingservice.common.status.ReceptionStatus;
import kr.co.seoulit.his.labimagingservice.laborder.dto.LabWorklistItemDto;
import kr.co.seoulit.his.labimagingservice.laborder.dto.WorklistStep;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabReceptionEntity;
import kr.co.seoulit.his.labimagingservice.laborder.mapper.LabWorklistMapper;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabOrderItemEntity;
import kr.co.seoulit.his.labimagingservice.laborder.repository.LabOrderItemRepository;
import kr.co.seoulit.his.labimagingservice.laborder.repository.LabReceptionRepository;
import kr.co.seoulit.his.labimagingservice.labresult.entity.LabResultEntity;
import kr.co.seoulit.his.labimagingservice.labresult.repository.LabResultRepository;
import kr.co.seoulit.his.labimagingservice.labschedule.entity.LabScheduleEntity;
import kr.co.seoulit.his.labimagingservice.labschedule.repository.LabScheduleRepository;
import kr.co.seoulit.his.labimagingservice.labspecimen.entity.SpecimenAcceptanceEntity;
import kr.co.seoulit.his.labimagingservice.labspecimen.entity.SpecimenEntity;
import kr.co.seoulit.his.labimagingservice.labspecimen.repository.SpecimenAcceptanceRepository;
import kr.co.seoulit.his.labimagingservice.labspecimen.repository.SpecimenRepository;
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
    private final LabWorklistMapper labWorklistMapper;

    /**
     * 워크리스트 조회.
     *
     * @param receptionStatusCode "ACCEPTED"=처리 대상, "EXCLUDED"=제외됨, null=전체
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

        return receptions.stream()
                .map(reception -> toItem(
                        reception,
                        scheduledAtByReceptionId.get(reception.getLabReceptionId()),
                        specimensByReceptionId.getOrDefault(reception.getLabReceptionId(), List.of()),
                        acceptanceBySpecimenId,
                        itemsByOrderId.getOrDefault(reception.getLabOrder().getLabOrderId(), List.of()),
                        resultByItemId))
                .toList();
    }

    /** 접수상태 필터에 따라 조회 메서드를 고른다. 값이 없거나 모르는 값이면 전체. */
    private List<LabReceptionEntity> findReceptionsBy(String receptionStatusCode) {
        if (ReceptionStatus.ACCEPTED.name().equals(receptionStatusCode)
                || ReceptionStatus.EXCLUDED.name().equals(receptionStatusCode)) {
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
                                      Map<String, LabResultEntity> resultByItemId) {

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
         * 재채취를 요청했다는 기록은 판정 이력에 그대로 남으므로, 요청이 있었다는 사실만으로는
         * 다시 채취해야 하는지 알 수 없다. 이미 다시 채취했을 수도 있기 때문이다.
         * 그래서 "검체 수가 재채취 요청 수보다 많으면 이미 다시 받은 것"으로 본다.
         *
         *   검체 1건 → 부적합·재채취요청 1건            : 1 <= 1  → 아직 재채취 안 함
         *   재채취해서 검체 2건, 재채취요청은 그대로 1건 : 2 <= 1 아님 → 해소됨
         *
         * ⚠ recollectionCount > 0 조건을 빠뜨리면 안 된다.
         *   검체도 판정도 없는 접수(0건)가 0 <= 0 으로 성립해, 재채취를 요청한 적도 없는데
         *   목록 전체에 "재채취" 표시가 붙는다.
         */
        boolean recollectionPending = recollectionCount > 0 && specimenCount <= recollectionCount;

        int labItemCount = orderItems.size();

        List<LabResultEntity> results = orderItems.stream()
                .map(item -> resultByItemId.get(item.getLabOrderItemId()))
                .filter(result -> result != null)
                .toList();

        int resultCount = results.size();
        int confirmedResultCount = (int) results.stream()
                .filter(result -> RESULT_STATUS_CONFIRMED.equals(result.getResultStatusCode()))
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
     * ⚠ RESULT 는 아직 결과 등록 기능이 없어 화면에서 비활성으로만 표시된다.
     *   그래도 값을 내려주는 이유는, 판정까지 끝낸 건이 목록에 계속 남는 이유를
     *   담당자가 알 수 있어야 하기 때문이다.
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
