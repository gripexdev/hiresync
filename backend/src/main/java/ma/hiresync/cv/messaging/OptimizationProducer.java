package ma.hiresync.cv.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.hiresync.config.RabbitMQConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OptimizationProducer {

    private final RabbitTemplate rabbitTemplate;

    public void send(OptimizationMessage message) {
        log.info("Publishing optimization job to RabbitMQ: {}", message.optimizationId());
        rabbitTemplate.convertAndSend(
            RabbitMQConfig.CV_EXCHANGE,
            RabbitMQConfig.CV_OPTIMIZE_KEY,
            message
        );
    }
}
