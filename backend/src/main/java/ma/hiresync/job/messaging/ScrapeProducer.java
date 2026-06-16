package ma.hiresync.job.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.hiresync.config.RabbitMQConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ScrapeProducer {

    static final List<String> SOURCES = List.of(
            "rekrute.com", "emploi.ma", "indeed.ma", "linkedin.com", "marocemploi.net");

    private final RabbitTemplate rabbitTemplate;

    /** Publishes one message per source — all 5 are consumed in parallel by ScrapeConsumer. */
    public void publishAll() {
        SOURCES.forEach(source ->
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.JOB_EXCHANGE,
                    RabbitMQConfig.JOB_SCRAPE_KEY,
                    new ScrapeMessage(source))
        );
        log.info("Queued {} scrape jobs: {}", SOURCES.size(), SOURCES);
    }

    public int sourceCount() { return SOURCES.size(); }
}
