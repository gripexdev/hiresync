package ma.hiresync.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology for:
 *   1. Async CV optimization  (cv.exchange → cv.optimize.queue)
 *   2. Async job scraping     (job.exchange → job.scrape.queue, concurrency 5)
 *   3. Async job enrichment   (job.exchange → job.enrich.queue,  concurrency 1)
 */
@Configuration
public class RabbitMQConfig {

    // ── CV optimization ───────────────────────────────────────────────────────
    public static final String CV_OPTIMIZE_QUEUE    = "cv.optimize.queue";
    public static final String CV_OPTIMIZE_DLQ      = "cv.optimize.dlq";
    public static final String CV_EXCHANGE          = "cv.exchange";
    public static final String CV_DL_EXCHANGE       = "cv.dl.exchange";
    public static final String CV_OPTIMIZE_KEY      = "cv.optimize";

    // ── Job scraping & enrichment ─────────────────────────────────────────────
    public static final String JOB_EXCHANGE         = "job.exchange";
    public static final String JOB_DL_EXCHANGE      = "job.dl.exchange";

    public static final String JOB_SCRAPE_QUEUE     = "job.scrape.queue";
    public static final String JOB_SCRAPE_DLQ       = "job.scrape.dlq";
    public static final String JOB_SCRAPE_KEY       = "job.scrape";

    public static final String JOB_ENRICH_QUEUE     = "job.enrich.queue";
    public static final String JOB_ENRICH_DLQ       = "job.enrich.dlq";
    public static final String JOB_ENRICH_KEY       = "job.enrich";

    // ── CV exchanges & queues ─────────────────────────────────────────────────
    @Bean public DirectExchange cvExchange()           { return new DirectExchange(CV_EXCHANGE,    true, false); }
    @Bean public DirectExchange cvDeadLetterExchange() { return new DirectExchange(CV_DL_EXCHANGE, true, false); }

    @Bean
    public Queue cvOptimizeQueue() {
        return QueueBuilder.durable(CV_OPTIMIZE_QUEUE)
                .withArgument("x-dead-letter-exchange",    CV_DL_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", CV_OPTIMIZE_KEY)
                .withArgument("x-message-ttl", 300_000)
                .build();
    }
    @Bean public Queue cvOptimizeDeadLetterQueue() { return QueueBuilder.durable(CV_OPTIMIZE_DLQ).build(); }

    @Bean public Binding cvOptimizeBinding(Queue cvOptimizeQueue, DirectExchange cvExchange) {
        return BindingBuilder.bind(cvOptimizeQueue).to(cvExchange).with(CV_OPTIMIZE_KEY);
    }
    @Bean public Binding cvDlqBinding(Queue cvOptimizeDeadLetterQueue, DirectExchange cvDeadLetterExchange) {
        return BindingBuilder.bind(cvOptimizeDeadLetterQueue).to(cvDeadLetterExchange).with(CV_OPTIMIZE_KEY);
    }

    // ── Job exchanges ─────────────────────────────────────────────────────────
    @Bean public DirectExchange jobExchange()           { return new DirectExchange(JOB_EXCHANGE,    true, false); }
    @Bean public DirectExchange jobDeadLetterExchange() { return new DirectExchange(JOB_DL_EXCHANGE, true, false); }

    // ── Job scrape queue ──────────────────────────────────────────────────────
    @Bean
    public Queue jobScrapeQueue() {
        return QueueBuilder.durable(JOB_SCRAPE_QUEUE)
                .withArgument("x-dead-letter-exchange",    JOB_DL_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", JOB_SCRAPE_KEY)
                .build();
    }
    @Bean public Queue jobScrapeDlq() { return QueueBuilder.durable(JOB_SCRAPE_DLQ).build(); }

    @Bean public Binding jobScrapeBinding(Queue jobScrapeQueue, DirectExchange jobExchange) {
        return BindingBuilder.bind(jobScrapeQueue).to(jobExchange).with(JOB_SCRAPE_KEY);
    }
    @Bean public Binding jobScrapeDlqBinding(Queue jobScrapeDlq, DirectExchange jobDeadLetterExchange) {
        return BindingBuilder.bind(jobScrapeDlq).to(jobDeadLetterExchange).with(JOB_SCRAPE_KEY);
    }

    // ── Job enrich queue ──────────────────────────────────────────────────────
    @Bean
    public Queue jobEnrichQueue() {
        return QueueBuilder.durable(JOB_ENRICH_QUEUE)
                .withArgument("x-dead-letter-exchange",    JOB_DL_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", JOB_ENRICH_KEY)
                .build();
    }
    @Bean public Queue jobEnrichDlq() { return QueueBuilder.durable(JOB_ENRICH_DLQ).build(); }

    @Bean public Binding jobEnrichBinding(Queue jobEnrichQueue, DirectExchange jobExchange) {
        return BindingBuilder.bind(jobEnrichQueue).to(jobExchange).with(JOB_ENRICH_KEY);
    }
    @Bean public Binding jobEnrichDlqBinding(Queue jobEnrichDlq, DirectExchange jobDeadLetterExchange) {
        return BindingBuilder.bind(jobEnrichDlq).to(jobDeadLetterExchange).with(JOB_ENRICH_KEY);
    }

    // ── Shared JSON converter & template ─────────────────────────────────────
    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory cf) {
        var t = new RabbitTemplate(cf);
        t.setMessageConverter(messageConverter());
        return t;
    }
}
