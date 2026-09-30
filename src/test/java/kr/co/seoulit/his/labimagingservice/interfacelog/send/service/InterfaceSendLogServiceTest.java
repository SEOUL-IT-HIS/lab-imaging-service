package kr.co.seoulit.his.labimagingservice.interfacelog.send.service;

import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.interfacelog.send.SendEventType;
import kr.co.seoulit.his.labimagingservice.interfacelog.send.entity.InterfaceSendLogEntity;
import kr.co.seoulit.his.labimagingservice.interfacelog.send.repository.InterfaceSendLogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 발신 이력 기록 규칙 (5차 Phase 5, D8/D10/D11).
 */
class InterfaceSendLogServiceTest {

    private final InterfaceSendLogRepository repository = mock(InterfaceSendLogRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final InterfaceSendStatusUpdater updater = mock(InterfaceSendStatusUpdater.class);
    private final InterfaceSendPublisher publisher = mock(InterfaceSendPublisher.class);

    private InterfaceSendLogService service(boolean kafkaEnabled) {
        return new InterfaceSendLogService(repository, events, updater, JsonMapper.builder().build(),
                kafkaEnabled ? Optional.of(publisher) : Optional.empty());
    }

    @BeforeEach
    void setUp() {
        // 업무 트랜잭션 안에서 불리는 상황을 흉내 낸다.
        TransactionSynchronizationManager.setActualTransactionActive(true);
        // mock 저장은 @PrePersist 를 돌리지 않으므로 ID 를 직접 채운다(실제 JPA 에서는 persist 시 생성된다).
        when(repository.save(any(InterfaceSendLogEntity.class))).thenAnswer(i -> {
            InterfaceSendLogEntity e = i.getArgument(0);
            ReflectionTestUtils.setField(e, "interfaceSendLogId", "log-new");
            return e;
        });
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    @DisplayName("recordPending → 01 로 저장하고 커밋 후 발행 이벤트를 낸다. 원문에 event_id 를 넣을 수 있다")
    void recordPending() {
        service(true).recordPending(SendEventType.RESULT, "res-1", "01", eventId -> Map.of("eventId", eventId));

        ArgumentCaptor<InterfaceSendLogEntity> saved = ArgumentCaptor.forClass(InterfaceSendLogEntity.class);
        verify(repository).save(saved.capture());
        InterfaceSendLogEntity log = saved.getValue();
        assertThat(log.getSendStatusCode()).isEqualTo("01");
        assertThat(log.getEventTypeCode()).isEqualTo("01");
        assertThat(log.getPayload()).contains(log.getEventId());
        verify(events).publishEvent(any(SendLogCreatedEvent.class));
    }

    @Test
    @DisplayName("같은 (유형, 원본)이 이미 있으면 기록·발행하지 않는다 (1회 발행)")
    void duplicateSkipped() {
        when(repository.existsByEventTypeCodeAndReferenceId("02", "item-1")).thenReturn(true);

        Optional<String> result = service(true).recordPending(SendEventType.BILLING, "item-1", "06", id -> Map.of());

        assertThat(result).isEmpty();
        verify(repository, never()).save(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    @DisplayName("recordFailed → 03 + 사유 (발행 이벤트는 내지 않는다)")
    void recordFailed() {
        service(true).recordFailed(SendEventType.BILLING, "item-9", "06", null, "수가코드 매핑 없음: 09");

        ArgumentCaptor<InterfaceSendLogEntity> saved = ArgumentCaptor.forClass(InterfaceSendLogEntity.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getSendStatusCode()).isEqualTo("03");
        assertThat(saved.getValue().getErrorMessage()).isEqualTo("수가코드 매핑 없음: 09");
        verify(events, never()).publishEvent(any());
    }

    @Test
    @DisplayName("트랜잭션 밖에서 부르면 막는다 (AFTER_COMMIT 발행이 안 도는 버그를 드러낸다 — D8 함정 4)")
    void outsideTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
        assertThatThrownBy(() -> service(true).recordPending(SendEventType.BILLING, "x", "06", id -> Map.of()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("수동 재전송: Kafka 비활성이면 LAB091, 원문 없으면 LAB092, 정상이면 retry+1 후 같은 행을 발행")
    void resend() {
        InterfaceSendLogEntity noPayload = InterfaceSendLogEntity.builder()
                .eventTypeCode("02").referenceId("a").systemCode("06").sendStatusCode("03").errorMessage("매핑 없음").build();
        InterfaceSendLogEntity failed = InterfaceSendLogEntity.builder()
                .eventTypeCode("02").referenceId("b").systemCode("06").payload("{}").sendStatusCode("03").build();
        when(repository.findById("np")).thenReturn(Optional.of(noPayload));
        when(repository.findById("f")).thenReturn(Optional.of(failed));

        assertThatThrownBy(() -> service(false).resend("f")).extracting("messageCode").isEqualTo(LabMessageCode.LAB091);
        assertThatThrownBy(() -> service(true).resend("np")).extracting("messageCode").isEqualTo(LabMessageCode.LAB092);

        service(true).resend("f");
        verify(updater).beginRetry("f");
        verify(publisher).publish("f");
    }
}
