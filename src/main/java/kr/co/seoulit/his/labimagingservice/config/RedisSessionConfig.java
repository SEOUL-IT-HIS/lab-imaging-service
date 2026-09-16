package kr.co.seoulit.his.labimagingservice.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import tools.jackson.databind.DefaultTyping;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

/**
 * 로그인 세션을 Redis에 공유 저장하기 위한 직렬화 설정 (2026-09-16 admin 팀 가이드).
 *
 * ⚠ 빈 이름 springSessionDefaultRedisSerializer 는 바꾸면 안 된다.
 *   Spring Session이 이 이름으로 찾아 쓰는 구조라, 이름이 다르면 에러 없이
 *   JDK 직렬화로 조용히 되돌아간다.
 */
@Configuration
public class RedisSessionConfig {

    /** "@class" 에 적힌 클래스를 되살릴 때 허용할 범위 (SessionUser 가 이 아래 있다) */
    private static final String ALLOWED_PACKAGE = "kr.co.seoulit.his.";

    @Bean
    public RedisSerializer<Object> springSessionDefaultRedisSerializer() {
        // 우리 패키지만 허용한다.
        // java.lang. 을 열어두면 ProcessBuilder 같은 위험한 클래스까지 들어온다.
        PolymorphicTypeValidator allowedTypes = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType(ALLOWED_PACKAGE)
                .build();

        return GenericJacksonJsonRedisSerializer.builder()
                .customize(builder -> builder.activateDefaultTyping(
                        allowedTypes,
                        DefaultTyping.NON_FINAL,
                        JsonTypeInfo.As.PROPERTY))
                .build();
    }
}
