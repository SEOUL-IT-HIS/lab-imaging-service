package kr.co.seoulit.his.labimagingservice.config;

import kr.co.seoulit.his.labimagingservice.common.exception.DuplicateOrderException;
import kr.co.seoulit.his.labimagingservice.common.exception.LabImagingBusinessException;
import kr.co.seoulit.his.labimagingservice.laborder.messaging.dto.EventEnvelope;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.util.backoff.ExponentialBackOff;
import org.apache.kafka.clients.admin.NewTopic;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka 설정 — 처방코어(OPD) ↔ 검사(LAB)
 *
 * ⚠ @ConditionalOnProperty 로 통째로 감쌌다.
 *   app.kafka.enabled=false 로 바꾸고 재기동하면 이 안의 빈이 하나도 만들어지지 않아
 *   Consumer/Producer 가 사라지고 기존 REST intake 경로만 남는다.
 *   시연 중 Kafka 가 막혔을 때 되돌릴 유일한 수단이라 플래그를 이 레벨에 둔다.
 *
 * ⚠ 직렬화는 JacksonJsonSerializer/Deserializer 를 쓴다. JsonSerializer/JsonDeserializer 가 아니다.
 *   이름이 비슷해서 헷갈리는데 둘은 쓰는 Jackson 이 다르다.
 *     JsonSerializer         → com.fasterxml (Jackson 2)
 *     JacksonJsonSerializer  → tools.jackson (Jackson 3)  ← Boot 4 는 이쪽
 *   Boot 4 는 com.fasterxml 의 ObjectMapper 빈을 등록하지 않아, 전자를 쓰면 기동이 막힌다.
 */
@Configuration
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class KafkaConfig {

    /** 재시도 간격 시작값 (1초) */
    private static final long BACKOFF_INITIAL_MS = 1000L;
    /** 재시도마다 간격을 2배로 */
    private static final double BACKOFF_MULTIPLIER = 2.0;
    /** 최초 시도 + 재시도 3회 = 총 4회 (OPD 문서 07장 제안값) */
    private static final long BACKOFF_MAX_ATTEMPTS = 3L;

    @Value("${app.kafka.topic.lab-order-requested}")
    private String requestedTopic;

    @Value("${app.kafka.topic.lab-order-resulted}")
    private String resultedTopic;

    /**
     * 우리가 구독하는 토픽.
     *
     * ⚠ 남이 발행하는 토픽인데도 우리가 선언한다.
     *   OPD 가 아직 한 번도 발행하지 않은 상태에서 우리 Consumer 가 먼저 뜨면
     *   토픽이 없어 "UNKNOWN_TOPIC_OR_PARTITION" 경고가 계속 찍힌다.
     *   먼저 만들어두면 시연 순서에 상관없이 조용히 뜬다.
     *
     * ⚠ 단일 노드 브로커라 복제는 1 이어야 한다. 2 이상이면 토픽 생성 자체가 실패한다.
     */
    @Bean
    public NewTopic labOrderRequestedTopic() {
        return TopicBuilder.name(requestedTopic).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic labOrderResultedTopic() {
        return TopicBuilder.name(resultedTopic).partitions(1).replicas(1).build();
    }

    /**
     * 수신 컨테이너 팩토리.
     *
     * ⚠ ErrorHandlingDeserializer 로 감싼다. 이게 없으면 역직렬화 실패가
     *   컨테이너 레벨에서 터져 같은 메시지를 무한 반복해서 읽는다(poison pill).
     *   감싸두면 실패가 레코드 단위 예외로 바뀌어 아래 에러 핸들러가 DLT 로 보낸다.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<?>> kafkaListenerContainerFactory(
            KafkaProperties kafkaProperties, DefaultErrorHandler errorHandler) {

        Map<String, Object> props = new HashMap<>(kafkaProperties.buildConsumerProperties());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JacksonJsonDeserializer.class);
        // 우리 패키지의 타입만 역직렬화 대상으로 허용한다. (임의 클래스 역직렬화 방지)
        props.put(JacksonJsonDeserializer.TRUSTED_PACKAGES,
                "kr.co.seoulit.his.labimagingservice.laborder.messaging.dto");
        /*
         * ⚠ 발신자가 타입 헤더를 붙여 보내도 무시하고, 우리가 정한 타입으로 읽는다.
         *   OPD 가 자기 클래스 이름을 헤더에 넣어 보내면 우리 쪽에 없는 클래스라 실패한다.
         *   계약은 "필드 모양"이지 "클래스 이름"이 아니다.
         */
        props.put(JacksonJsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        props.put(JacksonJsonDeserializer.VALUE_DEFAULT_TYPE, EventEnvelope.class.getName());

        ConsumerFactory<String, EventEnvelope<?>> consumerFactory =
                new DefaultKafkaConsumerFactory<>(props);

        ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<?>> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }

    /**
     * 에러 처리 — 재시도 3회(지수 백오프) 후 {topic}.DLT 로 이동.
     *
     * ⚠ 여기가 이 작업에서 가장 실수하기 쉬운 부분이다.
     *   환자ID가 틀렸거나 공통코드가 없는 건 100번 재시도해도 똑같이 실패한다.
     *   그런데도 재시도하면 같은 메시지를 4번 처리하고 DLT 로 보내는 동안
     *   정작 코어는 결과를 영영 못 받는다.
     *
     *   그래서 업무 예외는 재시도 대상에서 뺀다.
     *   Consumer 가 이 예외들을 직접 잡아 REJECTED 결과를 발행하고 정상 종료하므로
     *   실제로는 여기까지 올라오지도 않는다. 이 등록은 이중 안전장치다.
     *
     * ⚠ 역직렬화 실패도 재시도 대상이 아니다. 같은 바이트를 다시 파싱해도 결과가 같다.
     *   (DefaultErrorHandler 가 DeserializationException 을 기본으로 재시도 제외한다)
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> template) {
        ExponentialBackOff backOff = new ExponentialBackOff(BACKOFF_INITIAL_MS, BACKOFF_MULTIPLIER);
        backOff.setMaxAttempts(BACKOFF_MAX_ATTEMPTS);

        DefaultErrorHandler handler =
                new DefaultErrorHandler(new DeadLetterPublishingRecoverer(template), backOff);

        handler.addNotRetryableExceptions(
                LabImagingBusinessException.class,
                DuplicateOrderException.class);
        return handler;
    }
}
