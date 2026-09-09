package kr.co.seoulit.his.labimagingservice.billing.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 검사항목코드(labItemCode) → 수가코드(feeCode) 변환.
 *
 * ⚠ 2026-09-09 기준 실제 매핑표 미확정. 테스트용 임시값이다.
 *   수납팀에서 검사항목코드-수가코드 매핑표를 받으면 이 설정값만 교체한다.
 *   코드 구조는 이미 확정된 것이므로 값 교체 외 로직 변경은 불필요하다.
 *   (application.properties 의 app.billing.fee-code-mapping.{labItemCode}=... 참고 —
 *    지금은 전부 FEE002 로 통일된 테스트값이다)
 *
 * ⚠ 매핑에 없는 코드가 들어오면 예외를 던진다. 운영 중 매핑 누락(신규 검사항목 추가 시
 *   깜빡하는 경우)을 조용히 넘기면, 그 검사만 청구가 영원히 안 나가는데 아무도 모른다.
 *   대신 이 예외는 절대 API 응답까지 올라가면 안 된다 — 청구 발행은 결과 확정의 부수
 *   효과일 뿐이라, 확정 자체를 실패시키면 안 된다. 호출하는 쪽(LabResultService)이
 *   반드시 감싸서 로그만 남기고 넘어가야 한다. (LabImagingBusinessException 이 아니라
 *   순수 IllegalStateException 을 쓰는 이유이기도 하다 — GlobalExceptionHandler 가
 *   API 오류로 착각해 처리하지 않도록 의도적으로 다른 타입을 쓴다)
 */
@Slf4j
@Component
public class FeeCodeResolver {

    private final Map<String, String> feeCodeMapping;

    public FeeCodeResolver(Map<String, String> feeCodeMapping) {
        this.feeCodeMapping = feeCodeMapping;
    }

    /**
     * @throws IllegalStateException 매핑에 없는 labItemCode 인 경우
     */
    public String resolve(String labItemCode) {
        String feeCode = feeCodeMapping.get(labItemCode);
        if (feeCode == null) {
            log.error("수가코드 매핑이 없습니다. 청구 이벤트 발행을 건너뜁니다. labItemCode={} (등록된 매핑={})",
                    labItemCode, feeCodeMapping.keySet());
            throw new IllegalStateException("수가코드 매핑 누락: labItemCode=" + labItemCode);
        }
        return feeCode;
    }
}
