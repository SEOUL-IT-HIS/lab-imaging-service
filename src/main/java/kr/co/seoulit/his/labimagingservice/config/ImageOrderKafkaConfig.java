package kr.co.seoulit.his.labimagingservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * 영상오더 연계 토픽 (5차 Phase 8, D12).
 *
 * ⚠ KafkaConfig 에 넣지 않고 따로 둔 이유: 토픽 빈을 app.kafka.image-order.enabled=true 일 때만 만들어야 한다.
 *   처방코어가 아직 발행하지 않는 미합의 토픽을 브로커에 미리 만들어 두지 않는다(켜기 전 기존 동작 무영향 조건).
 * ⚠ 수신 컨테이너 팩토리·에러 핸들러(재시도 3회 → DLT)는 KafkaConfig 의 것을 그대로 쓴다.
 */
@Configuration
@ConditionalOnExpression("${app.kafka.enabled:false} and ${app.kafka.image-order.enabled:false}")
public class ImageOrderKafkaConfig {

    @Bean
    public NewTopic imageOrderRequestedTopic(@Value("${app.kafka.topic.image-order-requested}") String topic) {
        return TopicBuilder.name(topic).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic imageOrderResultedTopic(@Value("${app.kafka.topic.image-order-resulted}") String topic) {
        return TopicBuilder.name(topic).partitions(1).replicas(1).build();
    }
}
