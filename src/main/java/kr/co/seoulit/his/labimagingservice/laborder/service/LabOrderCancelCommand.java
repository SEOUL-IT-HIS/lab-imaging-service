package kr.co.seoulit.his.labimagingservice.laborder.service;

import java.util.List;

/**
 * 검사오더 취소 입력 — Kafka(LabOrderCancelledData)와 REST(LabOrderCancelRequestDto) 두 계약을
 * 하나로 모은 내부 전용 타입. (05번 지시서 2-B)
 *
 * ⚠ LabOrderIntakeRequestDto 와 같은 이유로 바깥 계약 DTO 를 그대로 서비스에 넘기지 않는다.
 *   어느 한쪽 계약이 바뀌어도 LabOrderCancelService 는 흔들리지 않는다.
 *   변환은 LabOrderCancelledConsumer.toCommand / LabOrderCancelController 쪽이 각자 담당한다.
 */
public record LabOrderCancelCommand(
        String prescriptionId,
        String cancelReason,
        String cancelledBy,
        List<Item> items) {

    public record Item(String itemCode, String labOrderId) {
    }
}
