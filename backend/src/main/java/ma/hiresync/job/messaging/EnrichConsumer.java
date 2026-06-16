package ma.hiresync.job.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.hiresync.config.RabbitMQConfig;
import ma.hiresync.job.service.JobService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Processes enrichment in batches of 20.
 * concurrency=1 — sequential by design (each batch reads the same enriched=false pool).
 * Self-requeues if more jobs remain, but only when this batch made progress —
 * prevents infinite loops if a source consistently fails enrichment.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EnrichConsumer {

    private final JobService     jobService;
    private final EnrichProducer enrichProducer;

    @RabbitListener(queues = RabbitMQConfig.JOB_ENRICH_QUEUE, concurrency = "1")
    public void consume(EnrichMessage msg) {
        log.info("[enrich] Batch triggered by: {}", msg.triggeredBy());

        var result = jobService.triggerEnrich();

        log.info("[enrich] enrichedThisRun={}, enrichedLeft={}",
                result.enrichedThisRun(), result.enrichedLeft());

        if (result.enrichedLeft() > 0 && result.enrichedThisRun() > 0) {
            enrichProducer.publish("self-requeue");
        }
    }
}
