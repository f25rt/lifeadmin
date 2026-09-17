package com.lifeadmin.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Publishes {@link DocumentEvent}s to RabbitMQ for the async worker (default). Active when
 * {@code lifeadmin.messaging.enabled=true}.
 */
@Component
@ConditionalOnProperty(prefix = "lifeadmin.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class AmqpDocumentEventPublisher implements DocumentEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final MessagingProperties properties;

    @Override
    public void publish(final DocumentEvent event) {
        rabbitTemplate.convertAndSend(
                properties.getDocumentExchange(),
                properties.getDocumentProcessingRoutingKey(),
                event);
    }
}
