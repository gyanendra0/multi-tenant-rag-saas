package com.tenantrag.backend.ingest;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Phase 7 (step 4) — RabbitMQ wiring for publishing ingestion jobs.
 *
 * <p>We publish to the <em>default exchange</em> using the queue name as the
 * routing key (the same pattern the worker's {@code publish_test} uses), so no
 * custom exchange/binding is needed. The queue is declared <b>durable</b> to
 * match the worker's declaration — both sides must agree or RabbitMQ rejects the
 * declaration with a {@code PRECONDITION_FAILED}.
 */
@Configuration
public class IngestQueueConfig {

    private final String queueName;

    public IngestQueueConfig(@Value("${app.ingest.queue}") String queueName) {
        this.queueName = queueName;
    }

    /** Durable queue so messages survive a broker restart (matches the worker). */
    @Bean
    public Queue ingestQueue() {
        return QueueBuilder.durable(queueName).build();
    }

    /** Serialize message bodies as JSON (so the Python worker can json.loads them). */
    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /** RabbitTemplate that uses the JSON converter. */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }
}
