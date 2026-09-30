package kr.co.seoulit.his.labimagingservice.labresult.type;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 검사항목코드(TEST_TYPE_CD) → 결과 유형(일반/미생물/병리). (5차 D1)
 *
 * 설정: app.lab-result.type.{검사항목코드}=MICROBIOLOGY | PATHOLOGY   (없으면 GENERAL)
 *
 * ⚠ admin 공통코드에 "속성" 컬럼이 없어서(ADMIN.COMMON_CODE = code_value/code_name/sort_order/use_yn 뿐)
 *   코드 자체로는 유형을 알 수 없다. 그래서 이 서비스 설정으로 판별한다. (Phase 0 조사 2·6번)
 *   admin 이 미생물·병리용 TEST_TYPE_CD 를 다른 번호로 등록하면 설정만 바꾸면 된다.
 *
 * ⚠ Map 을 @Bean 으로 만들지 않고 여기서 직접 바인딩한다. BillingConfig 가 이미
 *   Map<String, String> 빈(feeCodeMapping)을 만들고 있어, 같은 타입 빈이 하나 더 생기면
 *   FeeCodeResolver 의 타입 기반 주입이 어느 쪽을 받을지 모호해진다.
 *
 * ⚠ 기동 시점에 한 번만 읽는다. 매핑을 바꾸면 재기동해야 한다(feeCodeMapping 과 같다).
 */
@Slf4j
@Component
public class LabResultTypeResolver {

    static final String PREFIX = "app.lab-result.type";

    private final Map<String, LabResultType> typeByItemCode;

    /** ⚠ 생성자가 둘이라 스프링이 쓸 쪽을 @Autowired 로 지정한다(아래는 테스트 전용). */
    @Autowired
    public LabResultTypeResolver(Environment environment) {
        Map<String, String> raw = Binder.get(environment)
                .bind(PREFIX, Bindable.mapOf(String.class, String.class))
                .orElse(Map.of());

        Map<String, LabResultType> parsed = new LinkedHashMap<>();
        raw.forEach((code, type) -> {
            try {
                parsed.put(code, LabResultType.valueOf(type.trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                // 오타 하나로 기동이 막히면 곤란하다. 경고만 남기고 그 코드는 GENERAL 로 둔다.
                log.warn("{}.{}={} 는 알 수 없는 결과 유형입니다. GENERAL 로 처리합니다.", PREFIX, code, type);
            }
        });
        this.typeByItemCode = Collections.unmodifiableMap(parsed);
        log.info("검사항목 결과유형 매핑 {}건 적재: {}", typeByItemCode.size(), typeByItemCode);
    }

    /** 테스트용 — 매핑을 직접 넘긴다. */
    public LabResultTypeResolver(Map<String, LabResultType> typeByItemCode) {
        this.typeByItemCode = Map.copyOf(typeByItemCode);
    }

    public LabResultType resolve(String labItemCode) {
        if (labItemCode == null) {
            return LabResultType.GENERAL;
        }
        return typeByItemCode.getOrDefault(labItemCode, LabResultType.GENERAL);
    }
}
