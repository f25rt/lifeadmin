package com.lifeadmin.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology for async document processing (G4): a main work queue whose rejected/failed
 * messages (after listener retries are exhausted) are routed to a dead-letter exchange → DLQ. JSON
 * message conversion is used so {@link DocumentEvent} serializes cleanly.
 *
 * <p>Active only when {@code lifeadmin.messaging.enabled=true} (default). Tests set it false and
 * drive the processing service synchronously, avoiding a broker dependency.
 */
@Configuration
@ConditionalOnProperty(prefix = "lifeadmin.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RabbitConfig {

    @Bean
    public DirectExchange documentExchange(final MessagingProperties props) {
        return new DirectExchange(props.getDocumentExchange(), true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange(final MessagingProperties props) {
        return new DirectExchange(props.getDeadLetterExchange(), true, false);
    }

    @Bean
    public Queue documentProcessingQueue(final MessagingProperties props) {
        return QueueBuilder.durable(props.getDocumentProcessingQueue())
                .withArgument("x-dead-letter-exchange", props.getDeadLetterExchange())
                .withArgument("x-dead-letter-routing-key", props.getDocumentProcessingRoutingKey())
                .build();
    }

    @Bean
    public Queue deadLetterQueue(final MessagingProperties props) {
        return QueueBuilder.durable(props.getDeadLetterQueue()).build();
    }

    @Bean
    public Binding documentProcessingBinding(final MessagingProperties props) {
        return BindingBuilder.bind(documentProcessingQueue(props))
                .to(documentExchange(props))
                .with(props.getDocumentProcessingRoutingKey());
    }

    @Bean
    public Binding deadLetterBinding(final MessagingProperties props) {
        return BindingBuilder.bind(deadLetterQueue(props))
                .to(deadLetterExchange(props))
                .with(props.getDocumentProcessingRoutingKey());
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(final ConnectionFactory connectionFactory,
                                         final MessageConverter jsonMessageConverter) {
        final var template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }
}
