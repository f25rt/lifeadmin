package com.lifeadmin.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Binds {@code lifeadmin.messaging.*} — RabbitMQ topology names. Bean name: {@code messagingProperties}. */
@Component("messagingProperties")
@ConfigurationProperties(prefix = "lifeadmin.messaging")
public class MessagingProperties {

    private String documentExchange = "lifeadmin.document";
    private String documentProcessingQueue = "lifeadmin.document.processing";
    private String documentProcessingRoutingKey = "document.uploaded";
    private String deadLetterExchange = "lifeadmin.document.dlx";
    private String deadLetterQueue = "lifeadmin.document.processing.dlq";

    public String getDocumentExchange() { return documentExchange; }
    public void setDocumentExchange(String v) { this.documentExchange = v; }

    public String getDocumentProcessingQueue() { return documentProcessingQueue; }
    public void setDocumentProcessingQueue(String v) { this.documentProcessingQueue = v; }

    public String getDocumentProcessingRoutingKey() { return documentProcessingRoutingKey; }
    public void setDocumentProcessingRoutingKey(String v) { this.documentProcessingRoutingKey = v; }

    public String getDeadLetterExchange() { return deadLetterExchange; }
    public void setDeadLetterExchange(String v) { this.deadLetterExchange = v; }

    public String getDeadLetterQueue() { return deadLetterQueue; }
    public void setDeadLetterQueue(String v) { this.deadLetterQueue = v; }
}
