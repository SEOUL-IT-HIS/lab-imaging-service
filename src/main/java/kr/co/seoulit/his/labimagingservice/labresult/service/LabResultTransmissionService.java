package kr.co.seoulit.his.labimagingservice.labresult.service;

import kr.co.seoulit.his.labimagingservice.common.status.ReceptionStatus;
import kr.co.seoulit.his.labimagingservice.interfacelog.send.SendEventType;
import kr.co.seoulit.his.labimagingservice.interfacelog.send.service.InterfaceSendLogService;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabOrderEntity;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabOrderItemEntity;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabReceptionEntity;
import kr.co.seoulit.his.labimagingservice.laborder.messaging.dto.EventEnvelope;
import kr.co.seoulit.his.labimagingservice.laborder.repository.LabOrderItemRepository;
import kr.co.seoulit.his.labimagingservice.laborder.repository.LabReceptionRepository;
import kr.co.seoulit.his.labimagingservice.labresult.entity.LabResultDetailEntity;
import kr.co.seoulit.his.labimagingservice.labresult.entity.LabResultEntity;
import kr.co.seoulit.his.labimagingservice.labresult.messaging.dto.LabResultReportedData;
import kr.co.seoulit.his.labimagingservice.labresult.microbiology.entity.MicrobiologyResultEntity;
import kr.co.seoulit.his.labimagingservice.labresult.microbiology.repository.MicrobiologyResultRepository;
import kr.co.seoulit.his.labimagingservice.labresult.pathology.entity.PathologyResultEntity;
import kr.co.seoulit.his.labimagingservice.labresult.pathology.repository.PathologyResultRepository;
import kr.co.seoulit.his.labimagingservice.labresult.repository.LabResultRepository;
import kr.co.seoulit.his.labimagingservice.labresult.type.LabResultType;
import kr.co.seoulit.his.labimagingservice.labresult.type.LabResultTypeResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 검사결과 전송 (LAB → 처방코어·응급·병동). UC-RST-06 / ZP2-17: 117·119·120 (5차 Phase 6)
 *
 * 팀 공통 연동 원칙: 결과·상태 변화는 Kafka 이벤트 1건을 여러 서비스가 각자 다른 컨슈머 그룹으로 구독한다. 우리는 발행만 한다.
 *
 * ── 흐름: 일반·미생물·병리 결과 확정(02) → 여기서 봉투를 만들어 발신 이력(01, event_type_code=01)에 기록
 *   → 커밋 후 발행 → 실패 시 재처리·수동 재전송 (전부 5차 Phase 5 모듈 그대로)
 *   ⚠ 확정되지 않은 결과는 발행하지 않는다 — 이 서비스는 확정 메서드에서만 불린다.
 *   ⚠ 결과 1건 = 이벤트 1건(D10). 이력의 (01, reference_id=결과ID) UNIQUE 로 같은 결과를 두 번 보내지 않는다.
 *
 * ── 봉투: 검사오더 연계와 같은 EventEnvelope. eventId = 이력의 event_id(재발행해도 같다, 수신측 멱등키),
 *   eventType="LabResultReported", version="1.0", source="LAB", correlationId = labOrderId.
 *   토픽 lab.lab-result.reported.v1 — 처방코어(GR2) 제안 형식(2026-09-29)에 맞췄다. 메시지 키는 prescriptionId.
 *   ⚠ correlationId 를 labOrderId 로 둔 이유: 처방코어가 접수 결과(ACCEPTED)로 받은 labOrderId 를 상관키로 쓰기로 했다.
 *     결과는 하나의 요청에 대한 1:1 응답이 아니라(항목마다 따로 나간다) 요청 eventId 를 넣는 검사오더 회신과 다르다.
 *
 * ── 이 구조는 UC-RD-03 판독결과전송(6차)이 그대로 재사용하도록 두었다 — 새 SendEventType 값과 data 클래스만 추가하면 된다.
 *
 * ⚠ 예외를 던지지 않는다. 결과 전송은 확정의 부수 효과라, 무엇이 실패해도 확정은 성공한다(청구와 같은 원칙).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LabResultTransmissionService {

    static final String EVENT_TYPE = "LabResultReported";
    static final String VERSION = "1.0";
    static final String SOURCE = "LAB";
    private static final String STATUS_CONFIRMED = "02";
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final InterfaceSendLogService interfaceSendLogService;
    private final LabReceptionRepository labReceptionRepository;
    private final LabOrderItemRepository labOrderItemRepository;
    private final LabResultRepository labResultRepository;
    private final MicrobiologyResultRepository microbiologyResultRepository;
    private final PathologyResultRepository pathologyResultRepository;
    private final LabResultTypeResolver labResultTypeResolver;

    /**
     * ⚠ 6차: 이 결과에 결과항목(상세)이 있으면(details 비어있지 않음) 상위 resultValue/unit/
     *   referenceRange/abnormalFlag 는 전부 null 로 두고 items[].details[] 만 채운다(2-3).
     *   결과항목이 없는 검사(기존 방식)는 지금까지와 완전히 같다.
     *
     * ⚠ abnormalFlag 는 저장된 abnormal_yn(Y/N)을 그대로 옮기지 않는다. 처방코어가 고/저 방향을
     *   요구해(2026-09-30 회신) resultValue·referenceRange로 AbnormalYnDecider.decideDirection 을
     *   다시 계산한다 — 내부 저장용 이상여부(Y/N)와 외부 전송용 표현(N/H/L/null)은 의미가 다르다.
     */
    public void transmitGeneral(LabResultEntity result) {
        List<LabResultDetailEntity> details = result.getDetails();
        boolean detailMode = !details.isEmpty();
        send(result.getLabResultId(), result.getLabOrderItem(), null, result.getConfirmedAt(), b -> b
                .resultType(LabResultType.GENERAL.name())
                .resultValue(detailMode ? null : result.getResultValue())
                .unit(detailMode ? null : result.getResultUnit())
                .referenceRange(detailMode ? null : result.getReferenceRange())
                .abnormalFlag(detailMode ? null
                        : AbnormalYnDecider.decideDirection(result.getResultValue(), result.getReferenceRange()))
                .details(detailMode ? toReportedDetails(details) : null)
                .confirmedById(result.getConfirmedById()));
    }

    private static List<LabResultReportedData.Detail> toReportedDetails(List<LabResultDetailEntity> details) {
        return details.stream()
                .map(d -> LabResultReportedData.Detail.builder()
                        .resultItemCode(d.getResultItemCode())
                        .resultValue(d.getResultValue())
                        .unit(d.getResultUnit())
                        .referenceRange(d.getReferenceRange())
                        .abnormalFlag(AbnormalYnDecider.decideDirection(d.getResultValue(), d.getReferenceRange()))
                        .build())
                .toList();
    }

    /** @param microItem 접수의 미생물 항목(접수당 1개 제약). 못 찾으면 보내지 않는다. */
    public void transmitMicrobiology(MicrobiologyResultEntity result, LabOrderItemEntity microItem) {
        if (microItem == null) {
            log.warn("[RESULT-SEND] 미생물 결과의 검사항목을 정할 수 없어 전송하지 않습니다. resultId={}",
                    result.getMicrobiologyResultId());
            return;
        }
        send(result.getMicrobiologyResultId(), microItem, result.getSpecimen().getLabReception(), result.getConfirmedAt(), b -> b
                .resultType(LabResultType.MICROBIOLOGY.name())
                .cultureStatusCode(result.getCultureStatusCode())
                .organismCode(result.getOrganismCode())
                .causativeYn(result.getCausativeYn())
                .susceptibilities(result.getSusceptibilities().stream()
                        .map(s -> LabResultReportedData.Susceptibility.builder()
                                .antibioticCode(s.getAntibioticCode())
                                .susceptibilityResultCode(s.getSusceptibilityResultCode())
                                .build())
                        .toList())
                .confirmedById(result.getConfirmedById()));
    }

    public void transmitPathology(PathologyResultEntity result) {
        send(result.getPathologyResultId(), result.getLabOrderItem(), null, result.getConfirmedAt(), b -> b
                .resultType(LabResultType.PATHOLOGY.name())
                .pathologyTypeCode(result.getPathologyTypeCode())
                .diagnosisCode(result.getDiagnosisCode())
                .findings(result.getFindings())
                .attachmentYn(result.getAttachmentFileKey() == null ? "N" : "Y")
                .confirmedById(result.getConfirmedById()));
    }

    /**
     * @param reception 결과가 속한 접수. null 이면 오더의 처리 대상(ACCEPTED) 접수 중 최신을 쓴다.
     */
    private void send(String resultId, LabOrderItemEntity item, LabReceptionEntity reception, LocalDateTime confirmedAt,
                      Function<LabResultReportedData.Item.ItemBuilder, LabResultReportedData.Item.ItemBuilder> itemBody) {
        try {
            LabOrderEntity order = item.getLabOrder();
            LabReceptionEntity target = reception != null ? reception : latestAcceptedReception(order);
            int[] progress = progress(order);

            LabResultReportedData.Item reportedItem = itemBody.apply(LabResultReportedData.Item.builder()
                            .itemCode(item.getLabItemCode())
                            .itemName(null)
                            .labOrderItemId(item.getLabOrderItemId())
                            .resultId(resultId))
                    .build();

            LabResultReportedData data = LabResultReportedData.builder()
                    .prescriptionId(order.getLabOrderNo())
                    .labOrderId(order.getLabOrderId())
                    // 확정(02)만 발행하므로 항상 FINAL (PRELIMINARY 는 예약)
                    .resultStatus(LabResultReportedData.STATUS_FINAL)
                    .reportedAt(offset(confirmedAt))
                    .items(List.of(reportedItem))
                    .receptionNo(target == null ? null : target.getReceptionNo())
                    .patientId(order.getPatientId())
                    .systemCode(order.getSystemCode())
                    .treatTypeCode(order.getTreatTypeCode())
                    .urgencyYn(order.getUrgencyYn())
                    .confirmedItemCount(progress[0])
                    .totalItemCount(progress[1])
                    .build();

            // 수신처(system_code) = 요청 출처. 결과는 여러 서비스가 구독하지만 이력에는 1차 수신처를 적는다.
            // correlationId = labOrderId (처방코어 상관키)
            interfaceSendLogService.recordPending(SendEventType.RESULT, resultId, order.getSystemCode(),
                    eventId -> new EventEnvelope<>(eventId, EVENT_TYPE, VERSION, OffsetDateTime.now(SEOUL),
                            SOURCE, order.getLabOrderId(), data));

        } catch (Exception e) {
            log.error("[RESULT-SEND] 결과 전송 준비 실패 — 확정은 그대로 진행한다. resultId={}", resultId, e);
        }
    }

    /**
     * {확정된 결과 수, 검사항목 수} — 이 오더 기준. (이번 확정 포함)
     * ⚠ 결과 테이블이 유형마다 다르다(워크리스트 진행도와 같은 규칙). JPQL 조회 전에 Hibernate 가 방금 확정한 변경을
     *   자동 flush 하므로 이번 확정도 센다.
     */
    int[] progress(LabOrderEntity order) {
        List<LabOrderItemEntity> items = labOrderItemRepository.findByLabOrderIdIn(List.of(order.getLabOrderId()));
        List<String> itemIds = items.stream().map(LabOrderItemEntity::getLabOrderItemId).toList();
        if (itemIds.isEmpty()) {
            return new int[]{0, 0};
        }

        Map<String, String> general = labResultRepository.findByLabOrderItem_LabOrderItemIdIn(itemIds).stream()
                .collect(Collectors.toMap(r -> r.getLabOrderItem().getLabOrderItemId(), LabResultEntity::getResultStatusCode));
        Map<String, String> pathology = pathologyResultRepository.findByItemIds(itemIds).stream()
                .collect(Collectors.toMap(r -> r.getLabOrderItem().getLabOrderItemId(), PathologyResultEntity::getResultStatusCode));

        List<String> receptionIds = labReceptionRepository
                .findByLabOrder_LabOrderIdAndReceptionStatusCodeOrderByCreatedAtDesc(order.getLabOrderId(), ReceptionStatus.ACCEPTED.name())
                .stream().map(LabReceptionEntity::getLabReceptionId).toList();
        boolean microConfirmed = !receptionIds.isEmpty() && microbiologyResultRepository.findByReceptionIds(receptionIds).stream()
                .anyMatch(r -> STATUS_CONFIRMED.equals(r.getResultStatusCode()));

        int confirmed = 0;
        for (LabOrderItemEntity item : items) {
            String status = switch (labResultTypeResolver.resolve(item.getLabItemCode())) {
                case GENERAL -> general.get(item.getLabOrderItemId());
                case PATHOLOGY -> pathology.get(item.getLabOrderItemId());
                case MICROBIOLOGY -> microConfirmed ? STATUS_CONFIRMED : null;
            };
            if (STATUS_CONFIRMED.equals(status)) {
                confirmed++;
            }
        }
        return new int[]{confirmed, items.size()};
    }

    private LabReceptionEntity latestAcceptedReception(LabOrderEntity order) {
        List<LabReceptionEntity> receptions = labReceptionRepository
                .findByLabOrder_LabOrderIdAndReceptionStatusCodeOrderByCreatedAtDesc(order.getLabOrderId(), ReceptionStatus.ACCEPTED.name());
        return receptions.isEmpty() ? null : receptions.get(0);
    }

    private static OffsetDateTime offset(LocalDateTime value) {
        return value == null ? null : value.atZone(SEOUL).toOffsetDateTime();
    }
}
