package kr.co.seoulit.his.labimagingservice.interfacelog.send.service;

import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.dto.PageResponse;
import kr.co.seoulit.his.labimagingservice.common.exception.LabImagingBusinessException;
import kr.co.seoulit.his.labimagingservice.interfacelog.send.SendEventType;
import kr.co.seoulit.his.labimagingservice.interfacelog.send.dto.InterfaceSendLogDto;
import kr.co.seoulit.his.labimagingservice.interfacelog.send.entity.InterfaceSendLogEntity;
import kr.co.seoulit.his.labimagingservice.interfacelog.send.repository.InterfaceSendLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * 발신 이력 공통 모듈 — 기록·조회·수동 재전송. UC-COM-03 / UC-RST-06 (5차 Phase 5)
 * (수신 이력 InterfaceReceiveLogService 와 대칭. 판독결과전송 UC-RD-03 도 6차에 이 모듈을 그대로 쓴다)
 *
 * ══ 발행 흐름 (D8) ══
 *   1) 업무 트랜잭션 "안에서" recordPending → 01(전송대기) 행 저장 + SendLogCreatedEvent 발행
 *   2) 커밋 후 InterfaceSendPublisher(@TransactionalEventListener AFTER_COMMIT)가 Kafka 로 보낸다
 *   3) 발행 콜백이 02(완료)/03(실패)로 갱신한다(InterfaceSendStatusUpdater, 별도 트랜잭션)
 *   4) 실패(03)·오래된 대기(01)는 InterfaceSendRetryScheduler 가 같은 event_id 로 다시 보낸다
 *   발행 준비 자체가 안 되는 경우(수가코드 매핑 없음 등)는 recordFailed 로 03 을 남긴다 — 업무는 성공한다.
 *
 * ⚠ recordPending / recordFailed 에 @Transactional 을 붙이지 않았다. 일부러다.
 *   붙이면 이 메서드에서 난 예외가 (호출한 쪽이 잡더라도) 업무 트랜잭션을 rollback-only 로 만들어,
 *   "발행 기록 실패 때문에 확정이 롤백되는" 일이 생긴다. 수납팀과 합의한 원칙(발행 실패로 업무를
 *   되돌리지 않는다)에 어긋난다. 대신 "트랜잭션 안에서 불렸는가"를 직접 확인한다(D8 함정 4 —
 *   트랜잭션 밖에서 이벤트를 발행하면 AFTER_COMMIT 리스너가 아예 안 돈다).
 *
 * ⚠ Kafka 가 꺼져 있으면(app.kafka.enabled=false) 발행기 빈이 없다. 행은 01 로 쌓이기만 하고,
 *   나중에 켜면 재처리 스케줄러가 보낸다(D8).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterfaceSendLogService {

    private final InterfaceSendLogRepository interfaceSendLogRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final InterfaceSendStatusUpdater interfaceSendStatusUpdater;
    private final ObjectMapper objectMapper;
    /** Kafka 비활성이면 비어 있다 (@ConditionalOnProperty) */
    private final Optional<InterfaceSendPublisher> interfaceSendPublisher;

    // ------------------------------------------------------------------ 기록 (업무 트랜잭션 안)

    /**
     * 전송대기(01)로 기록하고, 커밋되면 발행되도록 예약한다.
     *
     * @param payloadFactory event_id 를 받아 발행할 메시지를 만든다. 봉투(EventEnvelope)에 event_id 를
     *                       넣어야 하는 결과전송 때문에 이 모양이다. (청구는 받은 값을 쓰지 않는다)
     * @return 기록한 행 ID. 같은 (유형, 원본)이 이미 있으면 기록하지 않고 empty (D10/D11 — 1회만 발행)
     */
    public Optional<String> recordPending(SendEventType type, String referenceId, String systemCode,
                                          Function<String, Object> payloadFactory) {
        assertInTransaction();
        if (interfaceSendLogRepository.existsByEventTypeCodeAndReferenceId(type.code(), referenceId)) {
            log.info("[SEND-LOG] 이미 기록된 발행이라 건너뜁니다. type={} referenceId={}", type, referenceId);
            return Optional.empty();
        }

        String eventId = UUID.randomUUID().toString();
        String payload = objectMapper.writeValueAsString(payloadFactory.apply(eventId));

        InterfaceSendLogEntity saved = interfaceSendLogRepository.save(InterfaceSendLogEntity.builder()
                .eventTypeCode(type.code())
                .eventId(eventId)
                .referenceId(referenceId)
                .systemCode(systemCode)
                .payload(payload)
                .sendStatusCode(InterfaceSendLogEntity.STATUS_PENDING)
                .build());

        applicationEventPublisher.publishEvent(new SendLogCreatedEvent(saved.getInterfaceSendLogId()));
        return Optional.of(saved.getInterfaceSendLogId());
    }

    /**
     * 발행 준비가 안 돼 보낼 수 없는 건을 실패(03)로 남긴다. (예: "수가코드 매핑 없음: {검사항목코드}")
     * ⚠ 조회 화면에 누락이 드러나게 하려는 목적이다(후속조치 #3). 업무 처리는 그대로 성공한다.
     */
    public void recordFailed(SendEventType type, String referenceId, String systemCode,
                             Object payloadOrNull, String errorMessage) {
        assertInTransaction();
        if (interfaceSendLogRepository.existsByEventTypeCodeAndReferenceId(type.code(), referenceId)) {
            return;
        }
        interfaceSendLogRepository.save(InterfaceSendLogEntity.builder()
                .eventTypeCode(type.code())
                .referenceId(referenceId)
                .systemCode(systemCode)
                .payload(payloadOrNull == null ? null : objectMapper.writeValueAsString(payloadOrNull))
                .sendStatusCode(InterfaceSendLogEntity.STATUS_FAILED)
                .errorMessage(errorMessage)
                .build());
        log.warn("[SEND-LOG] 발행하지 못해 실패(03)로 기록했습니다. type={} referenceId={} reason={}",
                type, referenceId, errorMessage);
    }

    private void assertInTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "발신 이력은 업무 트랜잭션 안에서 기록해야 합니다. (트랜잭션 밖이면 커밋 후 발행이 동작하지 않는다)");
        }
    }

    // ------------------------------------------------------------------ 조회 (ZP2-120)

    @Transactional(readOnly = true)
    public PageResponse<InterfaceSendLogDto> search(String eventTypeCode, String sendStatusCode,
                                                    LocalDateTime from, LocalDateTime to, int page, int size) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB114, "조회 기간이 올바르지 않습니다. (from=" + from + ", to=" + to + ")");
        }
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.of(
                interfaceSendLogRepository.search(blankToNull(eventTypeCode), blankToNull(sendStatusCode), from, to, pageable),
                log -> toDto(log, false));
    }

    @Transactional(readOnly = true)
    public InterfaceSendLogDto getDetail(String logId) {
        return toDto(findOrThrow(logId), true);
    }

    // ------------------------------------------------------------------ 수동 재전송

    /**
     * 수동 재전송. 최대 재시도 횟수를 넘긴 건도 사람이 다시 보낼 수 있다(5-1).
     * ⚠ 같은 event_id 로 보낸다. 원문(payload)을 그대로 쓰므로 새로 만들지 않는다.
     */
    public InterfaceSendLogDto resend(String logId) {
        InterfaceSendLogEntity log = findOrThrow(logId);
        if (InterfaceSendLogEntity.STATUS_SENT.equals(log.getSendStatusCode())) {
            // 이미 보낸 건을 또 보내면 수신측 중복 처리에 기대게 된다. 굳이 허용하지 않는다.
            throw new LabImagingBusinessException(LabMessageCode.LAB092, "이미 전송이 완료된 건입니다. (logId=" + logId + ")");
        }
        if (log.getPayload() == null) {
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB092,
                    "발행할 원문이 없어 재전송할 수 없습니다. 원인(" + log.getErrorMessage() + ")을 해결한 뒤 다시 처리해야 합니다.");
        }
        InterfaceSendPublisher publisher = interfaceSendPublisher.orElseThrow(() -> new LabImagingBusinessException(
                LabMessageCode.LAB091, "Kafka 가 비활성 상태라 재전송할 수 없습니다. (app.kafka.enabled=false)"));

        interfaceSendStatusUpdater.beginRetry(logId);
        publisher.publish(logId);
        return getDetail(logId);
    }

    // ------------------------------------------------------------------ 내부

    private InterfaceSendLogEntity findOrThrow(String logId) {
        return interfaceSendLogRepository.findById(logId)
                .orElseThrow(() -> new LabImagingBusinessException(
                        LabMessageCode.LAB089, "발신 이력을 찾을 수 없습니다. (logId=" + logId + ")"));
    }

    private InterfaceSendLogDto toDto(InterfaceSendLogEntity log, boolean withPayload) {
        String[] display = displayFields(log);
        return InterfaceSendLogDto.builder()
                .interfaceSendLogId(log.getInterfaceSendLogId())
                .eventTypeCode(log.getEventTypeCode())
                .eventId(log.getEventId())
                .referenceId(log.getReferenceId())
                .systemCode(log.getSystemCode())
                .sendStatusCode(log.getSendStatusCode())
                .retryCount(log.getRetryCount())
                .errorMessage(log.getErrorMessage())
                .sentAt(log.getSentAt())
                .createdAt(log.getCreatedAt())
                .receptionRef(display[0])
                .itemCode(display[1])
                .payload(withPayload ? log.getPayload() : null)
                .build();
    }

    /**
     * 목록 표시용 값(접수, 항목코드)을 원문에서 꺼낸다. 원문이 없거나 모양이 다르면 null — 표시용이라 실패해도 조용히 넘어간다.
     *   결과전송(01): data.receptionNo / data.testTypeCode
     *   청구(02)    : receptionId / itemName(검사·영상 항목코드)
     */
    private String[] displayFields(InterfaceSendLogEntity log) {
        if (log.getPayload() == null) {
            return new String[]{null, null};
        }
        try {
            JsonNode root = objectMapper.readTree(log.getPayload());
            if (SendEventType.RESULT.code().equals(log.getEventTypeCode())) {
                JsonNode data = root.path("data");
                return new String[]{textOrNull(data.path("receptionNo")), textOrNull(data.path("testTypeCode"))};
            }
            return new String[]{textOrNull(root.path("receptionId")), textOrNull(root.path("itemName"))};
        } catch (RuntimeException e) {
            return new String[]{null, null};
        }
    }

    private static String textOrNull(JsonNode node) {
        return node.isMissingNode() || node.isNull() ? null : node.asString();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
