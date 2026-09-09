package kr.co.seoulit.his.labimagingservice.billing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 청구 연동(수납) 설정.
 *
 * ⚠ 이 프로젝트 최초의 @ConfigurationProperties 사용이다. 다른 곳은 값 하나씩 @Value 로
 *   받는데, feeCodeMapping 은 "검사항목코드 → 수가코드" 여러 쌍을 한 번에 받아야 해서
 *   @Value 로는 자연스럽게 표현이 안 된다(맵 전체를 한 문자열 프로퍼티에 우겨넣어야 함).
 *
 * ⚠ 별도 POJO(예: FeeCodeMappingProperties) 를 만들지 않고 Map<String,String> 을 반환하는
 *   @Bean 메서드에 @ConfigurationProperties 를 바로 붙였다. Spring Boot 가 공식적으로
 *   지원하는 방식이고("서드파티 설정 바인딩"), application.properties 의
 *     app.billing.fee-code-mapping.01=TODO
 *     app.billing.fee-code-mapping.02=TODO
 *   형태가 그대로 key=01/02, value=TODO 인 Map 으로 바인딩된다 — 중간에 필드명이
 *   한 단계 더 끼어들지 않는다.
 */
@Configuration
public class BillingConfig {

    @Bean
    @ConfigurationProperties(prefix = "app.billing.fee-code-mapping")
    public Map<String, String> feeCodeMapping() {
        return new LinkedHashMap<>();
    }
}
