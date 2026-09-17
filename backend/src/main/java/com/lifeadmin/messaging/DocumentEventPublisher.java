package com.lifeadmin.messaging;

/**
 * Publishes document lifecycle events. Two implementations select by config:
 * <ul>
 *   <li>{@code AmqpDocumentEventPublisher} — publishes to RabbitMQ (async worker; default).</li>
 *   <li>{@code SynchronousDocumentEventPublisher} — processes inline (tests / no-broker dev).</li>
 * </ul>
 */
public interface DocumentEventPublisher {
    void publish(DocumentEvent event);

    /**
     * Whether publishing should be deferred until the current transaction commits. AMQP defers so
     * the async worker (reading in its own transaction) sees committed data; the synchronous
     * publisher runs inline within the same transaction and must NOT defer (otherwise it would never
     * run inside a rolled-back test transaction, and in prod would run before the row is visible to
     * its own new read).
     */
    default boolean deferUntilCommit() {
        return true;
    }
}
