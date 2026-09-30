package kr.co.seoulit.his.labimagingservice.interfacelog.send.entity;

import jakarta.persistence.*;
import kr.co.seoulit.his.labimagingservice.common.entity.BaseAuditEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 발신 이력 (INTERFACE_SEND_LOG) — 수신 이력(INTERFACE_RECEIVE_LOG)과 대칭. UC-COM-03 / UC-RST-06 (5차 Phase 5)
 *
 * 상태 (send_status_code, 공통코드 TRANSMIT_STATUS_CD — admin 실측 01 Pending / 02 Sent / 03 Failed)
 *   01 전송대기 → 02 전송완료
 *            ↘ 03 전송실패 → (재처리) → 02
 *
 * ⚠ event_id 는 행이 만들어질 때 한 번 정해지고 재발행해도 바뀌지 않는다(UX_ISLG_EVT UNIQUE).
 *   수신측은 이 값으로 중복을 거른다. 재처리 때 새 UUID 를 만들면 같은 결과가 두 번 반영된다.
 * ⚠ (event_type_code, reference_id) 는 UNIQUE 다(UX_ISLG_TYPE_REF). 같은 원본을 같은 유형으로 두 번 발행하지 않는다
 *   — 영상 청구는 최초 ACQUIRED 1회(D11), 결과 1건 = 전송 1건(D10). reference_id 는 항상 채운다(NULL 이면
 *   Oracle 복합 UNIQUE 판정이 어긋난다).
 * ⚠ sent_at 은 NOT NULL 이다. "마지막 발행(시도) 일시"로 쓴다 — 대기(01) 행은 등록 시각을 넣고,
 *   발행·재시도 때마다 갱신한다. 재처리 스케줄러가 "오래된 01"을 이 값으로 고른다.
 */
@Entity
@Table(name = "INTERFACE_SEND_LOG")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InterfaceSendLogEntity extends BaseAuditEntity {

    public static final String STATUS_PENDING = "01";
    public static final String STATUS_SENT = "02";
    public static final String STATUS_FAILED = "03";

    /** error_message 컬럼 길이 (VARCHAR2(500)) — 넘치면 잘라서 넣는다 */
    private static final int ERROR_MESSAGE_MAX = 500;

    @Id
    @Column(name = "interface_send_log_id", length = 36, nullable = false, updatable = false)
    private String interfaceSendLogId;

    @Column(name = "event_type_code", length = 10, nullable = false)
    private String eventTypeCode;

    @Column(name = "event_id", length = 36, nullable = false, updatable = false)
    private String eventId;

    /** 원본 ID (검사항목ID / 영상촬영항목ID / 결과ID 등) */
    @Column(name = "reference_id", length = 36)
    private String referenceId;

    /** 수신처 (SYSTEM_SOURCE_CD) */
    @Column(name = "system_code", length = 10, nullable = false)
    private String systemCode;

    /** 발행한(할) 메시지 원문 JSON. 재발행은 이 원문을 그대로 보낸다. */
    @Lob
    @Column(name = "payload")
    private String payload;

    @Column(name = "send_status_code", length = 10, nullable = false)
    private String sendStatusCode;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "error_message", length = ERROR_MESSAGE_MAX)
    private String errorMessage;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    @Builder
    public InterfaceSendLogEntity(String eventTypeCode, String eventId, String referenceId, String systemCode,
                                  String payload, String sendStatusCode, String errorMessage) {
        this.eventTypeCode = eventTypeCode;
        this.eventId = eventId;
        this.referenceId = referenceId;
        this.systemCode = systemCode;
        this.payload = payload;
        this.sendStatusCode = sendStatusCode;
        this.errorMessage = truncate(errorMessage);
        this.retryCount = 0;
        this.sentAt = LocalDateTime.now();
    }

    @PrePersist
    private void generateId() {
        if (this.interfaceSendLogId == null) {
            this.interfaceSendLogId = UUID.randomUUID().toString();
        }
        if (this.eventId == null) {
            this.eventId = UUID.randomUUID().toString();
        }
    }

    /** 재발행 직전 — 재시도 횟수를 올리고 시도 시각을 갱신한다. (상태는 결과 콜백이 정한다) */
    public void beginRetry() {
        this.retryCount = this.retryCount + 1;
        this.sentAt = LocalDateTime.now();
    }

    public void markSent() {
        this.sendStatusCode = STATUS_SENT;
        this.errorMessage = null;
        this.sentAt = LocalDateTime.now();
    }

    public void markFailed(String errorMessage) {
        this.sendStatusCode = STATUS_FAILED;
        this.errorMessage = truncate(errorMessage);
        this.sentAt = LocalDateTime.now();
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= ERROR_MESSAGE_MAX ? message : message.substring(0, ERROR_MESSAGE_MAX);
    }
}
