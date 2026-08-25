package kr.co.seoulit.his.labimagingservice.interfacelog.service;

import kr.co.seoulit.his.labimagingservice.interfacelog.entity.InterfaceOrderType;
import kr.co.seoulit.his.labimagingservice.interfacelog.entity.InterfaceReceiveLogEntity;
import kr.co.seoulit.his.labimagingservice.interfacelog.repository.InterfaceReceiveLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 연계 수신 로그 서비스
 *
 * ⚠ Service 인터페이스 없이 클래스로 바로 구현한다. 사유는 LabOrderService 주석 참고.
 *
 * ══ 이 클래스의 핵심은 REQUIRES_NEW 다 ══
 *
 * 두 메서드 모두 호출한 쪽의 트랜잭션에 합류하지 않고 자기 트랜잭션을 새로 연다.
 *
 * 같은 트랜잭션에 묶으면 이렇게 된다.
 *   오더 저장 실패 → 롤백 → 수신 로그도 함께 사라짐
 * 즉 "왜 실패했는지 보려고 만든 로그"가 정작 실패했을 때만 없어진다.
 * 성공한 요청의 기록만 남는 로그는 존재 이유가 없다.
 *
 * REQUIRES_NEW 로 분리하면 업무 트랜잭션이 롤백돼도 로그는 이미 커밋돼 남는다.
 *
 * ⚠ 짝이 되는 규칙: 이 서비스를 부르는 LabOrderIntakeService.intake() 에는 @Transactional 을
 *   걸지 않는다. 한쪽만 지켜서는 분리가 성립하지 않는다. (그쪽 주석 참고)
 */
@Service
@RequiredArgsConstructor
public class InterfaceReceiveLogService {

    /** 수신 직후의 상태. 아직 처리 결과를 모른다는 뜻이다. (result_code 가 VARCHAR2(10) 이라 8자면 들어간다) */
    private static final String RESULT_RECEIVED = "RECEIVED";

    private final InterfaceReceiveLogRepository interfaceReceiveLogRepository;

    /**
     * 수신 원문을 남기고 로그ID를 돌려준다.
     *
     * 업무 처리를 시작하기 "전에" 부른다. 처리 중에 무슨 일이 나든 원문은 이미 남아 있어야 한다.
     *
     * @return 생성된 로그ID. 처리가 끝난 뒤 markResult 에 그대로 넘긴다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String logReceived(InterfaceOrderType orderType, String systemCode, String rawMessage) {
        InterfaceReceiveLogEntity log = InterfaceReceiveLogEntity.builder()
                .orderTypeCode(orderType)
                .systemCode(systemCode)
                .rawMessage(rawMessage)
                .resultCode(RESULT_RECEIVED)
                .receivedAt(LocalDateTime.now())
                .build();

        return interfaceReceiveLogRepository.save(log).getInterfaceReceiveLogId();
    }

    /**
     * 처리 결과를 기록한다.
     *
     * ⚠ 로그ID로 못 찾아도 예외를 던지지 않는다.
     *   결과 기록이 실패했다고 해서 이미 끝난 업무 처리를 되돌릴 이유가 없고,
     *   여기서 예외가 나가면 정상 처리된 요청이 실패로 응답된다. 로그가 업무를 방해하면 안 된다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markResult(String logId, String resultCode, String errorMessage) {
        interfaceReceiveLogRepository.findById(logId)
                .ifPresent(log -> log.markResult(resultCode, errorMessage));
    }
}
