package kr.co.seoulit.his.labimagingservice.common.exception;

import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import lombok.Getter;

/**
 * 취소 이벤트가 가리키는 처방(prescriptionId)의 오더가 아직 접수되지 않았을 때. (LAB117)
 * (05번 지시서 Phase 2-D — 순서 역전 대응)
 *
 * ⚠ LabImagingBusinessException 을 상속하지 않는다 — 일부러다.
 *   KafkaConfig.kafkaErrorHandler 가 LabImagingBusinessException/DuplicateOrderException 을
 *   재시도 제외 목록에 올려 두었는데, 상속하면 이 예외도 같은 분류기(instanceof 기반)에 걸려
 *   재시도 대상에서 빠진다. 취소 이벤트가 접수 처리보다 먼저 도착하는 순서 역전은 재시도하면
 *   풀릴 수 있는 상황이라(LAB이 재기동하며 두 토픽을 동시에 읽는 경우 등), 반드시 기본
 *   재시도 정책(1s·2s·4s 후 DLT)을 타야 한다. (LoginRequiredException 과 같은 결의 분리)
 *
 * ⚠ REST 경로(LabOrderCancelController)에서는 GlobalExceptionHandler 가 이 타입을 따로 잡아
 *   404 로 응답한다 — LAB013(검사 접수 정보를 찾을 수 없음)이 아니라 "오더 자체가 아직
 *   접수되지 않았다"는 의미를 분명히 하려고 새 코드를 쓴다.
 */
@Getter
public class OrderNotYetReceivedException extends RuntimeException {

    private final String messageCode = LabMessageCode.LAB117;

    public OrderNotYetReceivedException(String message) {
        super(message);
    }
}
