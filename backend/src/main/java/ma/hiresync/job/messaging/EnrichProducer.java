package ma.hiresync.job.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.hiresync.config.RabbitMQConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EnrichProducer {

    private final RabbitTemplate rabbitTemplate;

    public void publish(String triggeredBy) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.JOB_EXCHANGE,
                RabbitMQConfig.JOB_ENRICH_KEY,
                new EnrichMessage(triggeredBy));
        log.info("Queued enrich batch (triggered by: {})", triggeredBy);
    }
}
