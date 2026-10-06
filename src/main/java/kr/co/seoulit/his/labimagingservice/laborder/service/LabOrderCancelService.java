package kr.co.seoulit.his.labimagingservice.laborder.service;

import kr.co.seoulit.his.labimagingservice.common.exception.OrderNotYetReceivedException;
import kr.co.seoulit.his.labimagingservice.common.status.CancelOutcome;
import kr.co.seoulit.his.labimagingservice.common.status.OrderItemStatus;
import kr.co.seoulit.his.labimagingservice.labresult.microbiology.repository.MicrobiologyResultRepository;
import kr.co.seoulit.his.labimagingservice.labresult.pathology.repository.PathologyResultRepository;
import kr.co.seoulit.his.labimagingservice.labresult.repository.LabResultRepository;
import kr.co.seoulit.his.labimagingservice.labresult.type.LabResultType;
import kr.co.seoulit.his.labimagingservice.labresult.type.LabResultTypeResolver;
import kr.co.seoulit.his.labimagingservice.laborder.dto.ItemCancelResult;
import kr.co.seoulit.his.labimagingservice.laborder.dto.LabOrderCancelResultDto;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabOrderEntity;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabOrderItemEntity;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabReceptionEntity;
import kr.co.seoulit.his.labimagingservice.laborder.repository.LabOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 처방 비활성화(검사오더 취소) 처리. (05번 지시서 Phase 2-B)
 *
 * ⚠ Kafka(LabOrderCancelledConsumer)와 REST(LabOrderCancelController, OPD messaging-enabled=false
 *   일 때의 롤백 경로)가 이 클래스의 같은 메서드를 부른다. 두 입구의 판정 결과가 갈리면 안 된다.
 *
 * ⚠ 클래스 레벨에 @Transactional 을 걸지 않는다. cancel() 메서드 하나에만 건다.
 *   (LabOrderIntakeService 와 같은 관례 — 이 클래스는 수신 로그를 직접 남기지 않지만,
 *   호출하는 Consumer/Controller 가 수신 기록을 트랜잭션 밖에서 관리하므로 경계를 맞춰 둔다)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LabOrderCancelService {

    private static final int CANCEL_REASON_MAX_LENGTH = 200;

    private final LabOrderRepository labOrderRepository;
    private final LabResultRepository labResultRepository;
    private final MicrobiologyResultRepository microbiologyResultRepository;
    private final PathologyResultRepository pathologyResultRepository;
    private final LabResultTypeResolver labResultTypeResolver;

    /**
     * 처방 취소를 처리한다.
     *
     * ⚠ 오더를 못 찾으면 OrderNotYetReceivedException 을 던진다(재시도 대상, LAB117).
     *   취소 이벤트가 접수 이벤트보다 먼저 도착하는 순서 역전 때문이다 — 토픽이 달라 같은 키라도
     *   순서가 보장되지 않는다(05번 지시서 2-D). 그 밖의 결과는 전부 "업무상 정상 처리"라
     *   예외를 던지지 않고 결과 DTO로 돌려준다.
     */
    @Transactional
    public LabOrderCancelResultDto cancel(LabOrderCancelCommand command) {
        LabOrderEntity order = labOrderRepository.findByLabOrderNo(command.prescriptionId())
                .orElseThrow(() -> new OrderNotYetReceivedException(
                        "해당 처방의 오더가 아직 접수되지 않았습니다. (prescriptionId=" + command.prescriptionId() + ")"));

        String reason = truncateReason(command.cancelReason());
        LocalDateTime requestedAt = LocalDateTime.now();

        List<LabOrderCancelResultDto.ItemResult> itemResults = new ArrayList<>();
        for (LabOrderCancelCommand.Item requested : command.items()) {
            itemResults.add(processItem(order, requested));
        }

        CancelOutcome outcome = resolveOutcome(order);

        // ⚠ 성공/거절 공통 기록 — 거절된 건도 "요청은 받았으나 막았다"를 검사실이 알아야 한다.
        order.getReceptions().forEach(reception ->
                reception.markCancelRequest(requestedAt, reason, command.cancelledBy(), outcome));

        if (outcome == CancelOutcome.CANCELLED) {
            order.cancel();
            order.getReceptions().forEach(LabReceptionEntity::cancel);
        }

        return LabOrderCancelResultDto.of(command.prescriptionId(), outcome, itemResults);
    }

    private LabOrderCancelResultDto.ItemResult processItem(LabOrderEntity order, LabOrderCancelCommand.Item requested) {
        LabOrderItemEntity item = findItem(order, requested.itemCode());

        if (item == null) {
            log.warn("[CANCEL] prescriptionId={} 에 itemCode={} 항목이 없습니다. (NOT_FOUND)",
                    order.getLabOrderNo(), requested.itemCode());
            return LabOrderCancelResultDto.ItemResult.of(
                    requested.itemCode(), ItemCancelResult.NOT_FOUND, "오더에 없는 항목입니다.");
        }

        crossCheckLabOrderId(order, item, requested);

        ItemCancelResult decision = decide(item, order);
        String message = switch (decision) {
            case ALREADY -> "이미 취소된 항목입니다.";
            case REFUSED_DONE -> "이미 결과가 등록되어 취소할 수 없습니다.";
            case REFUSED_PROG -> "검체가 이미 등록되어 취소할 수 없습니다.";
            case CANCELLED -> "취소되었습니다.";
            case NOT_FOUND -> "오더에 없는 항목입니다."; // 이 분기에는 도달하지 않는다(위에서 이미 처리).
        };

        if (decision == ItemCancelResult.CANCELLED) {
            item.cancel();
        }

        return LabOrderCancelResultDto.ItemResult.of(requested.itemCode(), decision, message);
    }

    /**
     * 항목 1건의 취소 판정. (05번 지시서 2-C, 위에서부터 첫 일치 적용)
     *
     * ⚠ 기준을 한 곳에 모은다 — 여기만 고치면 판정 기준 전체가 바뀐다. REFUSED_PROG 가
     *   접수 전체를 보수적으로 막는 임시 정책이라는 점은 anySpecimenRegistered 주석 참고.
     */
    private ItemCancelResult decide(LabOrderItemEntity item, LabOrderEntity order) {
        if (OrderItemStatus.CANCELLED.name().equals(item.getItemStatusCode())) {
            return ItemCancelResult.ALREADY;
        }
        if (hasAnyResult(item, order)) {
            return ItemCancelResult.REFUSED_DONE;
        }
        if (anySpecimenRegistered(order)) {
            return ItemCancelResult.REFUSED_PROG;
        }
        return ItemCancelResult.CANCELLED;
    }

    /**
     * 항목에 결과가 하나라도 있는가. 결과 유형별로 저장 테이블이 달라 확인 방법이 다르다.
     *
     * ⚠ MICROBIOLOGY 는 검체(접수) 단위 저장이라 항목ID로 바로 확인할 수 없다. LAB_ORDER_ITEM 이
     *   특정 접수를 가리키는 FK 가 없어(1:N 관계), 오더에 달린 모든 접수를 보수적으로 훑어
     *   그중 하나라도 미생물 결과가 있으면 이 항목도 결과가 있다고 본다.
     */
    private boolean hasAnyResult(LabOrderItemEntity item, LabOrderEntity order) {
        LabResultType type = labResultTypeResolver.resolve(item.getLabItemCode());
        return switch (type) {
            case GENERAL -> labResultRepository.existsByLabOrderItem_LabOrderItemId(item.getLabOrderItemId());
            case PATHOLOGY -> pathologyResultRepository.existsByLabOrderItem_LabOrderItemId(item.getLabOrderItemId());
            case MICROBIOLOGY -> order.getReceptions().stream()
                    .anyMatch(r -> microbiologyResultRepository.existsBySpecimen_LabReception_LabReceptionId(
                            r.getLabReceptionId()));
        };
    }

    /**
     * 오더에 달린 접수 중 검체가 하나라도 등록됐는가.
     *
     * ⚠ 보수적으로 "접수 전체 항목"을 막는 임시 정책이다(05번 지시서 2-C 비고).
     *   검체(SPECIMEN)는 검사항목이 아니라 접수 단위로 등록되어 "이 검체가 어느 항목 때문에
     *   채취됐는지"를 알 방법이 없다. 그래서 특정 항목만 골라 통과시키면, 사실은 그 검체로
     *   이미 진행 중인 항목을 취소해버릴 위험이 있다. 검체-항목 매핑이 생기면 이 메서드 하나만
     *   고쳐 항목 단위로 정밀화하면 된다 — 사용자와 합의된 임시 정책.
     */
    private boolean anySpecimenRegistered(LabOrderEntity order) {
        return order.getReceptions().stream().anyMatch(r -> !r.getSpecimens().isEmpty());
    }

    private LabOrderItemEntity findItem(LabOrderEntity order, String itemCode) {
        return order.getOrderItems().stream()
                .filter(i -> i.getLabItemCode().equals(itemCode))
                .findFirst()
                .orElse(null);
    }

    /**
     * 이벤트의 labOrderId 가 우리 오더ID 와 다르면 WARN 로그만 남긴다 — 거절하지 않는다.
     * 매칭 기준은 itemCode 다(05번 지시서 0-1). labOrderId 는 OPD 가 ACCEPTED 회신을 받은
     * 항목에만 있고, 교차 확인용일 뿐이다.
     */
    private void crossCheckLabOrderId(LabOrderEntity order, LabOrderItemEntity item, LabOrderCancelCommand.Item requested) {
        String labOrderId = requested.labOrderId();
        if (labOrderId != null && !labOrderId.equals(order.getLabOrderId())) {
            log.warn("[CANCEL] itemCode={} 의 labOrderId({})가 우리 오더ID({})와 다릅니다 — itemCode 기준으로 처리합니다.",
                    item.getLabItemCode(), labOrderId, order.getLabOrderId());
        }
    }

    /**
     * 오더 전체 취소 판정. (05번 지시서 2-B step4)
     * 오더의 모든 항목이 CANCELLED면 전체 취소, 일부면 PARTIAL, 하나도 없으면 REFUSED다.
     *
     * ⚠ 이번 이벤트로 새로 취소된 항목만이 아니라 "지금 이 순간 오더 전체 상태"를 기준으로 본다.
     *   그래야 중복 이벤트·부분 재전송이 섞여도 결과가 항상 같은 값으로 수렴한다.
     */
    private CancelOutcome resolveOutcome(LabOrderEntity order) {
        List<LabOrderItemEntity> items = order.getOrderItems();
        long cancelledCount = items.stream()
                .filter(i -> OrderItemStatus.CANCELLED.name().equals(i.getItemStatusCode()))
                .count();

        if (cancelledCount == items.size()) {
            return CancelOutcome.CANCELLED;
        }
        if (cancelledCount > 0) {
            return CancelOutcome.PARTIAL;
        }
        return CancelOutcome.REFUSED;
    }

    /** 200자 초과 시 잘라서 저장한다(05번 지시서 1-B — DB 컬럼 VARCHAR2(200)). */
    private String truncateReason(String reason) {
        if (reason == null || reason.length() <= CANCEL_REASON_MAX_LENGTH) {
            return reason;
        }
        return reason.substring(0, CANCEL_REASON_MAX_LENGTH);
    }
}
