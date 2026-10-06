package kr.co.seoulit.his.labimagingservice.laborder.service;

import kr.co.seoulit.his.labimagingservice.common.exception.OrderNotYetReceivedException;
import kr.co.seoulit.his.labimagingservice.common.status.CancelOutcome;
import kr.co.seoulit.his.labimagingservice.common.status.OrderItemStatus;
import kr.co.seoulit.his.labimagingservice.common.status.OrderStatus;
import kr.co.seoulit.his.labimagingservice.common.status.ReceptionStatus;
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
import kr.co.seoulit.his.labimagingservice.labspecimen.entity.SpecimenEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 처방 비활성화(검사오더 취소) 판정 로직 (05번 지시서 Phase 2-C, Phase 6).
 *
 * ⚠ LabOrderEntity/LabOrderItemEntity/LabReceptionEntity 는 실제 엔티티(빌더로 생성)를 쓴다.
 *   cancel()/getItemStatusCode() 등 상태 전이 자체가 테스트 대상이라, mock 으로는
 *   "진짜로 상태가 바뀌었는지"를 확인할 수 없다. 접수의 검체 목록(getSpecimens)만 실제
 *   JPA 양방향 동기화가 안 되는 부분이라 그 메서드만 Mockito.spy 로 덮어쓴다.
 */
class LabOrderCancelServiceTest {

    private final LabOrderRepository labOrderRepository = mock(LabOrderRepository.class);
    private final LabResultRepository labResultRepository = mock(LabResultRepository.class);
    private final MicrobiologyResultRepository microbiologyResultRepository = mock(MicrobiologyResultRepository.class);
    private final PathologyResultRepository pathologyResultRepository = mock(PathologyResultRepository.class);
    private final LabResultTypeResolver labResultTypeResolver =
            new LabResultTypeResolver(Map.of("MICRO", LabResultType.MICROBIOLOGY, "PATH", LabResultType.PATHOLOGY));

    private final LabOrderCancelService service = new LabOrderCancelService(
            labOrderRepository, labResultRepository, microbiologyResultRepository,
            pathologyResultRepository, labResultTypeResolver);

    private static final String PRESCRIPTION_ID = "RX-1";

    private LabOrderItemEntity item(String itemCode) {
        return LabOrderItemEntity.builder()
                .labItemCode(itemCode)
                .itemStatusCode(OrderItemStatus.REGISTERED.name())
                .build();
    }

    private LabReceptionEntity reception() {
        return LabReceptionEntity.builder()
                .receptionNo("LR-1")
                .receptionStatusCode(ReceptionStatus.ACCEPTED.name())
                .urgencyYn("N")
                .receivedById("SYSTEM")
                .ackSentYn("N")
                .build();
    }

    private LabOrderEntity order() {
        return LabOrderEntity.builder()
                .labOrderNo(PRESCRIPTION_ID)
                .systemCode("01")
                .treatTypeCode("01")
                .urgencyYn("N")
                .orderStatusCode(OrderStatus.RECEIVED.name())
                .build();
    }

    private LabOrderCancelCommand.Item cmdItem(String itemCode) {
        return new LabOrderCancelCommand.Item(itemCode, null);
    }

    private LabOrderCancelCommand command(List<LabOrderCancelCommand.Item> items) {
        return new LabOrderCancelCommand(PRESCRIPTION_ID, "오처방", "DOC-1", items);
    }

    @BeforeEach
    void setUp() {
        // ⚠ anyString() 이 아니라 any() 다 — 테스트 엔티티는 @PrePersist 가 안 일어나 ID 가 null 이고,
        //   anyString() 매처는 null 인자를 매칭하지 않아(Mockito 공지 사항) 스텁이 적용되지 않는다.
        when(labResultRepository.existsByLabOrderItem_LabOrderItemId(any())).thenReturn(false);
        when(pathologyResultRepository.existsByLabOrderItem_LabOrderItemId(any())).thenReturn(false);
        when(microbiologyResultRepository.existsBySpecimen_LabReception_LabReceptionId(any())).thenReturn(false);
    }

    @Test
    @DisplayName("오더를 못 찾으면 OrderNotYetReceivedException — 재시도 대상에서 빠지지 않게 다른 업무예외를 상속하지 않는다")
    void orderNotFoundThrowsRetryableException() {
        when(labOrderRepository.findByLabOrderNo(PRESCRIPTION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel(command(List.of(cmdItem("CBC")))))
                .isInstanceOf(OrderNotYetReceivedException.class);

        OrderNotYetReceivedException e = new OrderNotYetReceivedException("test");
        assertThat(e).isNotInstanceOf(kr.co.seoulit.his.labimagingservice.common.exception.LabImagingBusinessException.class);
        assertThat(e).isNotInstanceOf(kr.co.seoulit.his.labimagingservice.common.exception.DuplicateOrderException.class);
    }

    @Test
    @DisplayName("판정 — 오더에 없는 itemCode는 NOT_FOUND, 배치 전체를 실패시키지 않는다")
    void notFoundItemSkipped() {
        LabOrderItemEntity cbc = item("CBC");
        LabOrderEntity order = order();
        order.addOrderItem(cbc);
        order.addReception(reception());
        when(labOrderRepository.findByLabOrderNo(PRESCRIPTION_ID)).thenReturn(Optional.of(order));

        LabOrderCancelResultDto result = service.cancel(command(List.of(cmdItem("UNKNOWN"))));

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getResult()).isEqualTo(ItemCancelResult.NOT_FOUND);
        assertThat(cbc.getItemStatusCode()).isEqualTo(OrderItemStatus.REGISTERED.name());
    }

    @Test
    @DisplayName("판정 — 이미 취소된 항목은 ALREADY (멱등)")
    void alreadyCancelledItem() {
        LabOrderItemEntity cbc = item("CBC");
        cbc.cancel();
        LabOrderEntity order = order();
        order.addOrderItem(cbc);
        order.addReception(reception());
        when(labOrderRepository.findByLabOrderNo(PRESCRIPTION_ID)).thenReturn(Optional.of(order));

        LabOrderCancelResultDto result = service.cancel(command(List.of(cmdItem("CBC"))));

        assertThat(result.getItems().get(0).getResult()).isEqualTo(ItemCancelResult.ALREADY);
        assertThat(result.getOutcome()).isEqualTo(CancelOutcome.CANCELLED); // 유일한 항목이 이미 CANCELLED 상태라 전체취소로 수렴
    }

    @Test
    @DisplayName("판정 — 결과가 이미 있는 일반검사 항목은 REFUSED_DONE")
    void refusedDoneWhenGeneralResultExists() {
        LabOrderItemEntity cbc = item("CBC");
        LabOrderEntity order = order();
        order.addOrderItem(cbc);
        order.addReception(reception());
        when(labOrderRepository.findByLabOrderNo(PRESCRIPTION_ID)).thenReturn(Optional.of(order));
        when(labResultRepository.existsByLabOrderItem_LabOrderItemId(any())).thenReturn(true);

        LabOrderCancelResultDto result = service.cancel(command(List.of(cmdItem("CBC"))));

        assertThat(result.getItems().get(0).getResult()).isEqualTo(ItemCancelResult.REFUSED_DONE);
        assertThat(cbc.getItemStatusCode()).isEqualTo(OrderItemStatus.REGISTERED.name());
        assertThat(result.getOutcome()).isEqualTo(CancelOutcome.REFUSED);
    }

    @Test
    @DisplayName("판정 — 병리 결과가 이미 있는 항목(PATH)은 REFUSED_DONE")
    void refusedDoneWhenPathologyResultExists() {
        LabOrderItemEntity path = item("PATH");
        LabOrderEntity order = order();
        order.addOrderItem(path);
        order.addReception(reception());
        when(labOrderRepository.findByLabOrderNo(PRESCRIPTION_ID)).thenReturn(Optional.of(order));
        when(pathologyResultRepository.existsByLabOrderItem_LabOrderItemId(any())).thenReturn(true);

        LabOrderCancelResultDto result = service.cancel(command(List.of(cmdItem("PATH"))));

        assertThat(result.getItems().get(0).getResult()).isEqualTo(ItemCancelResult.REFUSED_DONE);
    }

    @Test
    @DisplayName("판정 — 미생물 결과가 이미 있으면(접수 단위) MICRO 항목도 REFUSED_DONE")
    void refusedDoneWhenMicrobiologyResultExists() {
        LabOrderItemEntity micro = item("MICRO");
        LabOrderEntity order = order();
        order.addOrderItem(micro);
        LabReceptionEntity reception = reception();
        order.addReception(reception);
        when(labOrderRepository.findByLabOrderNo(PRESCRIPTION_ID)).thenReturn(Optional.of(order));
        when(microbiologyResultRepository.existsBySpecimen_LabReception_LabReceptionId(any())).thenReturn(true);

        LabOrderCancelResultDto result = service.cancel(command(List.of(cmdItem("MICRO"))));

        assertThat(result.getItems().get(0).getResult()).isEqualTo(ItemCancelResult.REFUSED_DONE);
    }

    @Test
    @DisplayName("판정 — 접수에 검체가 하나라도 있으면 결과 없는 항목도 REFUSED_PROG (보수적으로 전체 차단)")
    void refusedProgWhenSpecimenRegistered() {
        LabOrderItemEntity cbc = item("CBC");
        LabOrderEntity order = order();
        order.addOrderItem(cbc);
        LabReceptionEntity reception = org.mockito.Mockito.spy(reception());
        when(reception.getSpecimens()).thenReturn(List.of(mock(SpecimenEntity.class)));
        order.addReception(reception);
        when(labOrderRepository.findByLabOrderNo(PRESCRIPTION_ID)).thenReturn(Optional.of(order));

        LabOrderCancelResultDto result = service.cancel(command(List.of(cmdItem("CBC"))));

        assertThat(result.getItems().get(0).getResult()).isEqualTo(ItemCancelResult.REFUSED_PROG);
        assertThat(cbc.getItemStatusCode()).isEqualTo(OrderItemStatus.REGISTERED.name());
    }

    @Test
    @DisplayName("판정 — 결과도 검체도 없으면 CANCELLED")
    void cancelledWhenNothingInProgress() {
        LabOrderItemEntity cbc = item("CBC");
        LabOrderEntity order = order();
        order.addOrderItem(cbc);
        order.addReception(reception());
        when(labOrderRepository.findByLabOrderNo(PRESCRIPTION_ID)).thenReturn(Optional.of(order));

        LabOrderCancelResultDto result = service.cancel(command(List.of(cmdItem("CBC"))));

        assertThat(result.getItems().get(0).getResult()).isEqualTo(ItemCancelResult.CANCELLED);
        assertThat(cbc.getItemStatusCode()).isEqualTo(OrderItemStatus.CANCELLED.name());
    }

    @Test
    @DisplayName("전체 취소 — 오더의 모든 항목이 취소되면 오더·접수도 CANCELLED 로 전환된다")
    void fullCancelCancelsOrderAndReception() {
        LabOrderItemEntity cbc = item("CBC");
        LabOrderItemEntity crp = item("CRP");
        LabOrderEntity order = order();
        order.addOrderItem(cbc);
        order.addOrderItem(crp);
        LabReceptionEntity reception = reception();
        order.addReception(reception);
        when(labOrderRepository.findByLabOrderNo(PRESCRIPTION_ID)).thenReturn(Optional.of(order));

        LabOrderCancelResultDto result = service.cancel(command(List.of(cmdItem("CBC"), cmdItem("CRP"))));

        assertThat(result.getOutcome()).isEqualTo(CancelOutcome.CANCELLED);
        assertThat(order.getOrderStatusCode()).isEqualTo(OrderStatus.CANCELLED.name());
        assertThat(reception.getReceptionStatusCode()).isEqualTo(ReceptionStatus.CANCELLED.name());
        assertThat(reception.getCancelOutcome()).isEqualTo(CancelOutcome.CANCELLED.name());
        assertThat(reception.getCancelReason()).isEqualTo("오처방");
        assertThat(reception.getCancelledById()).isEqualTo("DOC-1");
        assertThat(reception.getCancelRequestedAt()).isNotNull();
    }

    @Test
    @DisplayName("일부 취소 — 한 항목은 취소되고 한 항목은 거절되면 PARTIAL, 접수는 CANCELLED 로 전환되지 않는다")
    void partialCancelKeepsReceptionAccepted() {
        LabOrderItemEntity cbc = item("CBC"); // 취소될 항목
        LabOrderItemEntity crp = item("CRP"); // 거절될 항목(이미 결과 있음)
        LabOrderEntity order = order();
        order.addOrderItem(cbc);
        order.addOrderItem(crp);
        LabReceptionEntity reception = reception();
        order.addReception(reception);
        when(labOrderRepository.findByLabOrderNo(PRESCRIPTION_ID)).thenReturn(Optional.of(order));
        // CRP 항목 조회 시에만 결과가 있다고 답한다 — existsBy 는 ID 기반이라 두 항목을 구분하려면
        // 항목ID 가 필요하지만 테스트에서는 PrePersist 가 일어나지 않아 둘 다 null 이다.
        // 그래서 어떤 항목이 거절되는지는 등록 순서(CBC 먼저 CANCELLED)로만 확인한다.
        when(labResultRepository.existsByLabOrderItem_LabOrderItemId(any())).thenReturn(false, true);

        LabOrderCancelResultDto result = service.cancel(command(List.of(cmdItem("CBC"), cmdItem("CRP"))));

        assertThat(result.getOutcome()).isEqualTo(CancelOutcome.PARTIAL);
        assertThat(order.getOrderStatusCode()).isNotEqualTo(OrderStatus.CANCELLED.name());
        assertThat(reception.getReceptionStatusCode()).isEqualTo(ReceptionStatus.ACCEPTED.name());
        assertThat(reception.getCancelOutcome()).isEqualTo(CancelOutcome.PARTIAL.name());
    }

    @Test
    @DisplayName("전부 거절 — 모든 항목이 이미 결과가 있으면 REFUSED, 그래도 취소 요청 기록은 접수에 남는다")
    void allRefusedStillRecordsCancelRequest() {
        LabOrderItemEntity cbc = item("CBC");
        LabOrderEntity order = order();
        order.addOrderItem(cbc);
        LabReceptionEntity reception = reception();
        order.addReception(reception);
        when(labOrderRepository.findByLabOrderNo(PRESCRIPTION_ID)).thenReturn(Optional.of(order));
        when(labResultRepository.existsByLabOrderItem_LabOrderItemId(any())).thenReturn(true);

        LabOrderCancelResultDto result = service.cancel(command(List.of(cmdItem("CBC"))));

        assertThat(result.getOutcome()).isEqualTo(CancelOutcome.REFUSED);
        assertThat(reception.getReceptionStatusCode()).isEqualTo(ReceptionStatus.ACCEPTED.name());
        assertThat(reception.getCancelOutcome()).isEqualTo(CancelOutcome.REFUSED.name());
        assertThat(reception.getCancelReason()).isEqualTo("오처방");
        assertThat(reception.getCancelRequestedAt()).isNotNull();
    }

    @Test
    @DisplayName("오더에 접수가 여러 건이면 성공/거절과 무관하게 취소 요청 기록이 모든 접수에 남는다")
    void marksCancelRequestOnAllReceptionsOfOrder() {
        LabOrderItemEntity cbc = item("CBC");
        LabOrderEntity order = order();
        order.addOrderItem(cbc);
        LabReceptionEntity reception1 = reception();
        LabReceptionEntity reception2 = LabReceptionEntity.builder()
                .receptionNo("LR-2").receptionStatusCode(ReceptionStatus.ACCEPTED.name())
                .urgencyYn("N").receivedById("SYSTEM").ackSentYn("N").build();
        order.addReception(reception1);
        order.addReception(reception2);
        when(labOrderRepository.findByLabOrderNo(PRESCRIPTION_ID)).thenReturn(Optional.of(order));

        service.cancel(command(List.of(cmdItem("CBC"))));

        assertThat(reception1.getCancelRequestedAt()).isNotNull();
        assertThat(reception2.getCancelRequestedAt()).isNotNull();
        // 전체 취소(유일한 항목이 취소됨)라 두 접수 모두 CANCELLED 로 전환된다.
        assertThat(reception1.getReceptionStatusCode()).isEqualTo(ReceptionStatus.CANCELLED.name());
        assertThat(reception2.getReceptionStatusCode()).isEqualTo(ReceptionStatus.CANCELLED.name());
    }

    @Test
    @DisplayName("취소 사유가 200자를 넘으면 잘라서 기록한다")
    void truncatesLongCancelReason() {
        LabOrderItemEntity cbc = item("CBC");
        LabOrderEntity order = order();
        order.addOrderItem(cbc);
        LabReceptionEntity reception = reception();
        order.addReception(reception);
        when(labOrderRepository.findByLabOrderNo(PRESCRIPTION_ID)).thenReturn(Optional.of(order));

        String longReason = "가".repeat(250);
        LabOrderCancelCommand cmd = new LabOrderCancelCommand(PRESCRIPTION_ID, longReason, "DOC-1", List.of(cmdItem("CBC")));
        service.cancel(cmd);

        assertThat(reception.getCancelReason()).hasSize(200);
    }

    @Test
    @DisplayName("이벤트의 labOrderId 가 우리 오더ID 와 달라도 WARN 로그만 남기고 itemCode 기준으로 처리한다")
    void crossCheckMismatchDoesNotBlock() {
        LabOrderItemEntity cbc = item("CBC");
        LabOrderEntity order = order();
        order.addOrderItem(cbc);
        order.addReception(reception());
        when(labOrderRepository.findByLabOrderNo(PRESCRIPTION_ID)).thenReturn(Optional.of(order));

        LabOrderCancelCommand.Item mismatched = new LabOrderCancelCommand.Item("CBC", "LO-mismatch");
        LabOrderCancelResultDto result = service.cancel(command(List.of(mismatched)));

        assertThat(result.getItems().get(0).getResult()).isEqualTo(ItemCancelResult.CANCELLED);
    }
}
