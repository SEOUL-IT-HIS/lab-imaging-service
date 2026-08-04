package kr.co.seoulit.his.labimagingservice.config;

import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * 타 서비스 REST 호출용 공통 설정.
 *
 * 2026-08 팀 결정: 공통코드를 제외한 외부 서비스 호출은 WebClient가 아니라 RestTemplate으로 통일한다.
 *
 * ⚠ 서비스별 RestTemplate이 아니라 "공용 Bean 1개"를 모든 Client가 재사용하는 방식이다.
 *   - 현재 환자/admin 서비스 호출의 설정 요구사항이 동일하다(타임아웃 3초, 인증 헤더·인터셉터 없음).
 *     서비스마다 Bean을 나누면 완전히 같은 설정이 복제될 뿐 얻는 게 없다.
 *   - base URL(host/port)은 RestTemplate에 rootUri로 굽지 않고 각 Client가 생성자 주입으로
 *     따로 들고 있다. 그래서 Bean 하나를 공유해도 서비스 간 경로가 섞이지 않는다.
 *   반대로 Bean을 분리해야 하는 시점: 특정 서비스만 인증 토큰 인터셉터/에러핸들러가 필요하거나,
 *   타임아웃 정책이 달라질 때. 그때 @Qualifier로 나누는 것이 맞다.
 *
 * ⚠ Spring Boot 4부터 RestTemplateBuilder의 패키지가 바뀌었다.
 *   org.springframework.boot.web.client (Boot 3) → org.springframework.boot.restclient (Boot 4)
 *   또한 starter-webmvc에 포함되지 않아 build.gradle에 spring-boot-restclient 의존성이 필요하다.
 */
@Configuration
public class RestTemplateConfig {

    /** 연결 타임아웃 3초 */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);

    /** 응답 대기(읽기) 타임아웃 3초 */
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(CONNECT_TIMEOUT)
                .readTimeout(READ_TIMEOUT)
                .build();
    }
}
