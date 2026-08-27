package kr.co.seoulit.his.labimagingservice.laborder.messaging.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;

/**
 * 이벤트 공통 봉투 (처방코어 OPD ↔ 검사 LAB)
 * OPD 문서 lab-order-kafka-spec Draft v0.1 04장 스펙 그대로다.
 *
 * ⚠ occurredAt 은 OffsetDateTime 이다. LocalDateTime 으로 받으면 "+09:00" 오프셋을 잃는다.
 *   두 서비스가 다른 타임존에서 돌 가능성을 남겨두려면 오프셋을 버리면 안 된다.
 *
 * ⚠ @JsonIgnoreProperties(ignoreUnknown = true) 를 붙인 이유 —
 *   OPD 가 봉투에 필드를 하나 추가하는 순간 우리 Consumer 가 전부 죽으면 안 된다.
 *   모르는 필드는 그냥 버린다. 연계 계약은 한쪽이 먼저 늘어나는 게 정상이다.
 *
 * ⚠ 애노테이션만 com.fasterxml.jackson.annotation 이다. Jackson 3 으로 넘어가면서
 *   databind/core 는 tools.jackson 으로 옮겨갔지만 애노테이션 패키지는 그대로 남았다.
 *   ObjectMapper 를 쓸 때는 tools.jackson.databind 를 import 해야 한다. (섞어 쓰면 빈 주입 실패)
 *
 * @param <T> 이벤트별 payload 타입 (LabOrderRequestedData / LabOrderResultedData)
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class EventEnvelope<T> {

    /** 이벤트 고유 식별자. ⚠ 멱등 처리의 기준 키다. (INTERFACE_RECEIVE_LOG.event_id) */
    private String eventId;

    /** LabOrderRequested / LabOrderResulted */
    private String eventType;

    /** 계약 버전. 현재 "1.0" */
    private String version;

    private OffsetDateTime occurredAt;

    /** 발행 주체. OPD 또는 LAB */
    private String source;

    /**
     * 요청↔결과 매칭 키.
     * ⚠ 결과를 발행할 때는 수신한 requested 이벤트의 eventId 를 여기에 넣는다. (문서 04장 규정)
     *   코어가 "어느 요청에 대한 응답인지"를 이 값으로 잇는다.
     */
    private String correlationId;

    private T data;
}
