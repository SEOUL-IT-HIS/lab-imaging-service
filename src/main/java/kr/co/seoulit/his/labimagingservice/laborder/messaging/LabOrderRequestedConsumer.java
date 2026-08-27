package kr.co.seoulit.his.labimagingservice.laborder.messaging;

import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.exception.DuplicateOrderException;
import kr.co.seoulit.his.labimagingservice.common.exception.LabImagingBusinessException;
import kr.co.seoulit.his.labimagingservice.interfacelog.entity.InterfaceOrderType;
import kr.co.seoulit.his.labimagingservice.interfacelog.entity.InterfaceReceiveLogEntity;
import kr.co.seoulit.his.labimagingservice.interfacelog.service.InterfaceReceiveLogService;
import kr.co.seoulit.his.labimagingservice.laborder.dto.LabOrderIntakeRequestDto;
import kr.co.seoulit.his.labimagingservice.laborder.dto.LabOrderIntakeResultDto;
import kr.co.seoulit.his.labimagingservice.laborder.messaging.dto.EventEnvelope;
import kr.co.seoulit.his.labimagingservice.laborder.messaging.dto.LabOrderRequestedData;
import kr.co.seoulit.his.labimagingservice.laborder.messaging.dto.LabOrderResultedData;
import kr.co.seoulit.his.labimagingservice.laborder.service.LabOrderIntakeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

/**
 * 검사오더 요청 수신 (OPD → LAB)
 * 토픽: opd.lab-order.requested.v1
 *
 * ⚠ 저장 로직을 여기서 다시 만들지 않는다. LabOrderIntakeService.intake() 를 호출만 한다.
 *   그 클래스는 처음부터 "Kafka 로 바뀌어도 그대로 재사용한다"를 전제로 작성됐고,
 *   변환·저장·수신로그가 모두 거기 들어 있다. REST 입구와 Kafka 입구가 같은 경로로 모여야
 *   두 경로의 결과가 갈리지 않는다.
 *
 * ⚠ 업무 실패는 재시도하지 않는다. 여기서 잡아 REJECTED 결과를 발행하고 정상 종료한다.
 *   환자ID 오류·공통코드 오류·중복 처방은 100번 다시 해도 결과가 같다.
 *   재시도하면 같은 메시지를 4번 처리하고 DLT 로 보내는 동안 코어는 결과를 못 받는다.
 *   코어 입장에서 "거절됨"은 실패가 아니라 정상 응답이다.
 *
 * ⚠ 그 밖의 예외(DB 커넥션 끊김 등)는 다시 던진다. 그건 재시도하면 성공할 수 있는 실패다.
 *   에러 핸들러가 3회 재시도 후 DLT 로 보낸다. (KafkaConfig.kafkaErrorHandler)
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
@RequiredArgsConstructor
public class LabOrderRequestedConsumer {

    /** 수신 출처 (공통코드 SYSTEM_SOURCE_CD). 처방코어는 외래 채널이다. */
    private static final String SYSTEM_CODE_OUTPATIENT = "OP";

    private final LabOrderIntakeService labOrderIntakeService;
    private final LabOrderResultedProducer labOrderResultedProducer;
    private final InterfaceReceiveLogService interfaceReceiveLogService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${app.kafka.topic.lab-order-requested}",
            groupId = "${app.kafka.consumer-group.lab-order-requested}")
    public void onLabOrderRequested(EventEnvelope<LabOrderRequestedData> envelope) {

        LabOrderRequestedData data = toRequestedData(envelope.getData());
        String eventId = envelope.getEventId();

        log.info("[KAFKA-IN ] opd.lab-order.requested.v1 | eventId={} | prescriptionId={} | items={}",
                shortId(eventId), data.getPrescriptionId(),
                data.getOrderItems() == null ? 0 : data.getOrderItems().size());

        /*
         * ① 멱등 확인.
         *
         * ⚠ "이미 기록이 있다"와 "이미 처리가 끝났다"는 다르다.
         *   수신 직후 남기는 기록의 결과코드는 RECEIVED = "처리 중"이다.
         *   재시도로 다시 들어온 이벤트가 이 상태면 앞 시도가 끝나지 않은 것이므로 다시 처리해야 한다.
         *   여기서 완료로 오해하면 아직 처리도 안 된 요청을 거절로 회신해 버린다.
         *   (2026-08-27 실제로 이 버그를 겪음 — 환자서비스 장애로 재시도가 걸리자 REJECTED 가 나갔다)
         *
         * ⚠ 결과가 확정된 이벤트는 무시하지 않고 저장된 결과를 "다시 발행"한다.
         *   코어가 결과를 못 받아 재전송한 경우인데, 무시하면 코어는 영영 결과를 모른다.
         */
        Optional<InterfaceReceiveLogEntity> previous =
                interfaceReceiveLogService.findByEventId(eventId);

        if (previous.isPresent() && isFinished(previous.get())) {
            republishPreviousResult(previous.get(), data, eventId);
            return;
        }

        /*
         * ② 수신 기록.
         *
         * ⚠ 앞 시도가 남긴 행이 있으면 그 행을 재사용한다. 새로 넣으면 event_id 조건부 UNIQUE
         *   (UX_IRLG_EVENT)에 걸린다. 같은 이벤트는 어떤 경우에도 한 행이어야 한다.
         */
        String logId = previous
                .map(InterfaceReceiveLogEntity::getInterfaceReceiveLogId)
                .orElseGet(() -> interfaceReceiveLogService.logReceived(
                        InterfaceOrderType.LAB, SYSTEM_CODE_OUTPATIENT, toRawMessage(data), eventId));

        try {
            // ③ 코어 계약 → REST 수신 계약으로 변환한 뒤 기존 경로에 태운다.
            LabOrderIntakeResultDto result =
                    labOrderIntakeService.intake(toIntakeRequest(data));

            interfaceReceiveLogService.markResult(logId, LabMessageCode.LAB001, null);
            labOrderResultedProducer.publish(
                    LabOrderResultedData.accepted(data.getPrescriptionId(), result.getLabOrderId()),
                    eventId);

        } catch (DuplicateOrderException e) {
            finishRejected(logId, eventId, data, e.getMessageCode(), e.getMessage());

        } catch (LabImagingBusinessException e) {
            finishRejected(logId, eventId, data, e.getMessageCode(), e.getMessage());
        }
        /*
         * ④ 그 밖의 예외는 잡지 않는다 → 에러 핸들러가 재시도·DLT 로 처리한다.
         *
         * ⚠ 이때 수신 기록을 일부러 RECEIVED 로 남겨 둔다.
         *   결과코드를 채우면 "끝난 이벤트"가 되어 재시도가 위 ① 에서 걸러진다.
         *   재시도해서 성공할 수 있는 실패이므로 미완 상태로 두는 게 맞다.
         *   재시도를 모두 소진해 DLT 로 가면 이 행은 RECEIVED 로 남는데,
         *   그건 "끝내 처리되지 못했다"는 사실을 정확히 나타낸다.
         */
    }

    /** 결과코드가 채워졌으면 처리가 끝난 이벤트다. RECEIVED 는 아직 처리 중이라는 뜻이다. */
    private boolean isFinished(InterfaceReceiveLogEntity log) {
        return !InterfaceReceiveLogService.RESULT_RECEIVED.equals(log.getResultCode());
    }

    /** 업무 거절 마무리 — 결과를 기록하고 REJECTED 를 발행한다. 예외는 다시 던지지 않는다. */
    private void finishRejected(String logId, String eventId,
                                LabOrderRequestedData data, String resultCode, String reason) {
        log.info("[KAFKA-OUT] 업무 거절 | eventId={} | reason={}", shortId(eventId), reason);

        interfaceReceiveLogService.markResult(logId, resultCode, reason);
        labOrderResultedProducer.publish(
                LabOrderResultedData.rejected(data.getPrescriptionId(), reason), eventId);
    }

    /**
     * 이미 처리한 이벤트의 결과를 다시 발행한다.
     *
     * ⚠ 처음 처리 때의 결과코드로 성공/거절을 되살린다.
     *   LAB001 이면 접수됐다는 뜻이고, 그 외(LAB004/LAB017/LAB998 등)는 거절이다.
     *
     * ⚠ 한계 — 그때의 labOrderId 를 로그 테이블에 따로 남기지 않아 ACCEPTED 재발행에는
     *   labOrderId 가 비어 나간다. 코어는 prescriptionId 로 이미 접수를 알고 있으므로
     *   "중복 요청이 거절되지 않고 접수된 상태"임을 아는 데는 충분하다.
     *   정확히 채우려면 로그에 결과 식별자 컬럼을 추가해야 하는데 지금 범위에는 과하다.
     */
    private void republishPreviousResult(InterfaceReceiveLogEntity previous,
                                         LabOrderRequestedData data, String eventId) {
        boolean accepted = LabMessageCode.LAB001.equals(previous.getResultCode());

        log.info("[KAFKA-DUP] eventId={} 이미 처리된 이벤트 — 이전 결과 재발행 (resultCode={})",
                shortId(eventId), previous.getResultCode());

        LabOrderResultedData result = accepted
                ? LabOrderResultedData.accepted(data.getPrescriptionId(), null)
                : LabOrderResultedData.rejected(data.getPrescriptionId(),
                        previous.getErrorMessage() == null
                                ? "이미 처리된 요청입니다." : previous.getErrorMessage());

        labOrderResultedProducer.publish(result, eventId);
    }

    /**
     * 봉투의 data 를 목표 타입으로 맞춘다.
     *
     * ⚠ EventEnvelope 가 제네릭이라 역직렬화 시점에 T 가 지워진다(type erasure).
     *   그래서 data 는 우리가 원하는 클래스가 아니라 Map 으로 들어온다.
     *   여기서 한 번 변환해 두면 나머지 코드는 타입을 그대로 믿고 쓸 수 있다.
     */
    private LabOrderRequestedData toRequestedData(Object rawData) {
        if (rawData instanceof LabOrderRequestedData typed) {
            return typed;
        }
        return objectMapper.convertValue(rawData, LabOrderRequestedData.class);
    }

    /**
     * Kafka 계약 → REST 수신 계약 변환.
     *
     * ⚠ 두 계약의 필드가 지금은 같지만 클래스를 나눠 두었다.
     *   OPD 가 Kafka 스펙을 바꿔도 REST intake 와 프론트 수동 등록 폼이 흔들리지 않아야 한다.
     *
     * ⚠ itemName 은 옮기지 않는다. 표시명은 admin 공통코드에서 읽는다.
     *   (LabOrderIntakeService.toCreateRequest 와 같은 이유)
     *
     * ⚠ 순수 변환만 한다. 수신 기록은 호출한 쪽이 이미 남겼다.
     *   변환 함수가 DB 를 건드리면 재시도 때 행이 중복으로 쌓인다.
     */
    private LabOrderIntakeRequestDto toIntakeRequest(LabOrderRequestedData data) {
        List<LabOrderIntakeRequestDto.Item> items = data.getOrderItems() == null
                ? List.of()
                : data.getOrderItems().stream()
                        .map(item -> new LabOrderIntakeRequestDto.Item(item.getItemCode(), null))
                        .toList();

        return new LabOrderIntakeRequestDto(
                data.getPrescriptionId(),
                data.getEncounterId(),
                data.getPatientId(),
                data.getDoctorId(),
                items);
    }

    /** 수신 원문. 직렬화가 실패해도 처리를 막지 않는다. */
    private String toRawMessage(LabOrderRequestedData data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JacksonException e) {
            return "원문 직렬화 실패: " + e.getMessage();
        }
    }

    /** 로그를 한눈에 보려고 UUID 앞 8자리만 찍는다. 추적에는 이 정도면 충분하다. */
    private String shortId(String id) {
        return (id == null || id.length() < 8) ? id : id.substring(0, 8);
    }
}
