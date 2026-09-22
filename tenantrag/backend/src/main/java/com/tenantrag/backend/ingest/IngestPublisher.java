package com.tenantrag.backend.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Phase 7 (step 4) — publishes ingestion jobs to RabbitMQ.
 *
 * <p>Publishes to the default exchange ({@code ""}) with the queue name as the
 * routing key, which delivers straight to that queue.
 */
@Component
public class IngestPublisher {

    private static final Logger log = LoggerFactory.getLogger(IngestPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final String queueName;

    public IngestPublisher(RabbitTemplate rabbitTemplate,
                           @Value("${app.ingest.queue}") String queueName) {
        this.rabbitTemplate = rabbitTemplate;
        this.queueName = queueName;
    }

    /** Send a job to the ingestion queue. Serialized as JSON by the converter. */
    public void publish(IngestMessage message) {
        rabbitTemplate.convertAndSend(queueName, message);
        log.info("Published ingest job for document {}", message.documentId());
    }
}
