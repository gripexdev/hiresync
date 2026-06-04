package ma.hiresync.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology for async CV optimization.
 *
 * Flow: CvController → cv.optimize.queue → CvOptimizationConsumer
 *                                              → OpenRouter API (Mistral 7B)
 *                                              → PostgreSQL
 *                                              → WebSocket push
 */
@Configuration
public class RabbitMQConfig {

    // Queue names
    public static final String CV_OPTIMIZE_QUEUE    = "cv.optimize.queue";
    public static final String CV_OPTIMIZE_DLQ      = "cv.optimize.dlq";     // dead-letter

    // Exchange names
    public static final String CV_EXCHANGE          = "cv.exchange";
    public static final String CV_DL_EXCHANGE       = "cv.dl.exchange";

    // Routing keys
    public static final String CV_OPTIMIZE_KEY      = "cv.optimize";

    // ── Exchange ──────────────────────────────────────────────────────────────
    @Bean
    public DirectExchange cvExchange() {
        return new DirectExchange(CV_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange cvDeadLetterExchange() {
        return new DirectExchange(CV_DL_EXCHANGE, true, false);
    }

    // ── Queues ────────────────────────────────────────────────────────────────
    @Bean
    public Queue cvOptimizeQueue() {
        return QueueBuilder.durable(CV_OPTIMIZE_QUEUE)
                .withArgument("x-dead-letter-exchange", CV_DL_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", CV_OPTIMIZE_KEY)
                .withArgument("x-message-ttl", 300_000)   // 5 min TTL
                .build();
    }

    @Bean
    public Queue cvOptimizeDeadLetterQueue() {
        return QueueBuilder.durable(CV_OPTIMIZE_DLQ).build();
    }

    // ── Bindings ──────────────────────────────────────────────────────────────
    @Bean
    public Binding cvOptimizeBinding(Queue cvOptimizeQueue, DirectExchange cvExchange) {
        return BindingBuilder.bind(cvOptimizeQueue).to(cvExchange).with(CV_OPTIMIZE_KEY);
    }

    @Bean
    public Binding cvDlqBinding(Queue cvOptimizeDeadLetterQueue, DirectExchange cvDeadLetterExchange) {
        return BindingBuilder.bind(cvOptimizeDeadLetterQueue).to(cvDeadLetterExchange).with(CV_OPTIMIZE_KEY);
    }

    // ── JSON message converter ────────────────────────────────────────────────
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
