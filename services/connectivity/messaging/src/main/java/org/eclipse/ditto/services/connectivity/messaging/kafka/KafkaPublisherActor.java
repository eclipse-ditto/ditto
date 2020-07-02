/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.connectivity.messaging.kafka;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.MessageSendingFailedException;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.connectivity.messaging.BasePublisherActor;
import org.eclipse.ditto.services.connectivity.util.ConnectionLogUtil;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.OutboundSignal;
import org.eclipse.ditto.services.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.services.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.signals.acks.base.Acknowledgement;
import org.eclipse.ditto.signals.base.Signal;

import akka.Done;
import akka.actor.Props;
import akka.actor.Status;
import akka.japi.pf.ReceiveBuilder;
import akka.util.ByteString;

/**
 * Responsible for publishing {@link org.eclipse.ditto.services.models.connectivity.ExternalMessage}s into an Kafka
 * broker.
 */
final class KafkaPublisherActor extends BasePublisherActor<KafkaPublishTarget> {

    private static final AcknowledgementLabel NO_ACK_LABEL = AcknowledgementLabel.of("ditto-kafka-diagnostic");

    /**
     * List of retryable producer exceptions.
     * Please keep up-to-date against {@link org.apache.kafka.clients.producer.Callback}.
     * Cannot check for containment as set as these classes are not final.
     * Keep the classes fully qualified to ensure they stay in the right package.
     */
    private static final List<Class<?>> RETRYABLE_EXCEPTIONS = List.of(
            org.apache.kafka.common.errors.CorruptRecordException.class,
            org.apache.kafka.common.errors.InvalidMetadataException.class,
            org.apache.kafka.common.errors.NotEnoughReplicasAfterAppendException.class,
            org.apache.kafka.common.errors.NotEnoughReplicasException.class,
            org.apache.kafka.common.errors.OffsetOutOfRangeException.class,
            org.apache.kafka.common.errors.TimeoutException.class,
            org.apache.kafka.common.errors.UnknownTopicOrPartitionException.class
    );

    static final String ACTOR_NAME = "kafkaPublisher";

    private final DittoDiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    private final KafkaConnectionFactory connectionFactory;
    private final boolean dryRun;

    private Producer<String, String> producer;

    @SuppressWarnings("unused")
    private KafkaPublisherActor(final Connection connection, final KafkaConnectionFactory factory,
            final boolean dryRun) {

        super(connection);
        this.dryRun = dryRun;
        connectionFactory = factory;

        startInternalKafkaProducer(connection);
        reportInitialConnectionState();
    }

    /**
     * Creates Akka configuration object {@link akka.actor.Props} for this {@code BasePublisherActor}.
     *
     * @param connection the connection this publisher belongs to.
     * @param factory the factory to create Kafka connections with.
     * @param dryRun whether this publisher is only created for a test or not.
     * @return the Akka configuration Props object.
     */
    static Props props(final Connection connection, final KafkaConnectionFactory factory, final boolean dryRun) {

        return Props.create(KafkaPublisherActor.class, connection, factory, dryRun);
    }

    @Override
    public void postStop() throws Exception {
        closeProducer();
        super.postStop();
    }

    @Override
    protected void preEnhancement(final ReceiveBuilder receiveBuilder) {
        receiveBuilder.match(OutboundSignal.Mapped.class, this::isDryRun,
                outbound -> log.info("Message dropped in dry run mode: {}", outbound))
                .matchEquals(GracefulStop.INSTANCE, unused -> this.stopGracefully());
    }

    @Override
    protected void postEnhancement(final ReceiveBuilder receiveBuilder) {
        // noop
    }

    @Override
    protected KafkaPublishTarget toPublishTarget(final String address) {
        return KafkaPublishTarget.fromTargetAddress(address);
    }

    @Override
    protected CompletionStage<Acknowledgement> publishMessage(final Signal<?> signal,
            @Nullable final Target autoAckTarget,
            final KafkaPublishTarget publishTarget,
            final ExternalMessage message,
            final int ackSizeQuota) {

        if (producer == null) {
            final MessageSendingFailedException error = MessageSendingFailedException.newBuilder()
                    .message("Kafka producer is not available.")
                    .dittoHeaders(signal.getDittoHeaders())
                    .build();
            escalate(error, "Requested to send Kafka message without producer; this is a bug.");
            return CompletableFuture.failedFuture(error);
        } else {
            final ProducerRecord<String, String> record = producerRecord(publishTarget, message);
            final CompletableFuture<Acknowledgement> resultFuture = new CompletableFuture<>();
            final Callback callBack = new ProducerCallBack(signal, autoAckTarget, ackSizeQuota, resultFuture,
                    this::escalateIfNotRetryable, connection);
            producer.send(record, callBack);
            return resultFuture;
        }
    }

    /**
     * Check a send exception.
     * Escalate to parent if it cannot be recovered from.
     * Called by ProducerCallBack; must be thread-safe.
     *
     * @param exception the exception.
     */
    private void escalateIfNotRetryable(final Exception exception) {
        if (!isRetryable(exception)) {
            escalate(exception, "Got non-retryable exception");
        }
    }

    private boolean isDryRun() {
        return dryRun;
    }

    private static ProducerRecord<String, String> producerRecord(final KafkaPublishTarget publishTarget,
            final ExternalMessage externalMessage) {

        final String payload = mapExternalMessagePayload(externalMessage);
        final Iterable<Header> headers = mapExternalMessageHeaders(externalMessage);

        return new ProducerRecord<>(publishTarget.getTopic(),
                publishTarget.getPartition().orElse(null),
                publishTarget.getKey().orElse(null),
                payload, headers);
    }

    private static Iterable<Header> mapExternalMessageHeaders(final ExternalMessage externalMessage) {
        return externalMessage.getHeaders()
                .entrySet()
                .stream()
                .map(header -> new RecordHeader(header.getKey(), header.getValue().getBytes(StandardCharsets.UTF_8)))
                .collect(Collectors.toList());
    }

    private static String mapExternalMessagePayload(final ExternalMessage externalMessage) {
        if (externalMessage.isTextMessage()) {
            return externalMessage.getTextPayload().orElse("");
        } else if (externalMessage.isBytesMessage()) {
            return externalMessage.getBytePayload()
                    .map(ByteString::fromByteBuffer)
                    .map(ByteString::utf8String)
                    .orElse("");
        } else {
            return "";
        }
    }

    private void startInternalKafkaProducer(final Connection connection) {
        logWithConnectionId().info("Starting internal Kafka producer.");
        closeProducer();
        // TODO: configure timeouts so that senders won't have to wait >1m for send failures.
        producer = connectionFactory.newProducer();
    }

    private void closeProducer() {
        if (producer != null) {
            // Give up any buffered messages and close the producer immediately.
            producer.close(0, TimeUnit.MILLISECONDS);
        }
    }

    private void stopInternalKafkaProducer() {
        logWithConnectionId().info("Stopping internal Kafka producer.");
        closeProducer();
    }

    private void reportInitialConnectionState() {
        logWithConnectionId().info("Publisher ready");
        getContext().getParent().tell(new Status.Success(Done.done()), getSelf());
    }

    @Override
    protected DittoDiagnosticLoggingAdapter log() {
        return logWithConnectionId();
    }

    private DittoDiagnosticLoggingAdapter logWithConnectionId() {
        ConnectionLogUtil.enhanceLogWithConnectionId(log, connectionId);
        return log;
    }

    private void stopGracefully() {
        stopInternalKafkaProducer();
        logWithConnectionId().debug("Stopping myself.");
        getContext().stop(getSelf());
    }

    private static boolean isRetryable(final Exception exception) {
        return RETRYABLE_EXCEPTIONS.stream().anyMatch(clazz -> clazz.isInstance(exception));
    }

    /**
     * Message that allows gracefully stopping the publisher actor.
     */
    static final class GracefulStop {

        static final GracefulStop INSTANCE = new GracefulStop();

        private GracefulStop() {
            // intentionally empty
        }

    }

    private static final class ProducerCallBack implements Callback {

        private final Signal<?> signal;
        @Nullable private final Target autoAckTarget;
        private final int ackSizeQuota;
        private final CompletableFuture<Acknowledgement> resultFuture;
        private final Consumer<Exception> checkException;
        private int currentQuota;
        private final Connection connection;

        private ProducerCallBack(final Signal<?> signal,
                @Nullable final Target autoAckTarget,
                final int ackSizeQuota,
                final CompletableFuture<Acknowledgement> resultFuture,
                final Consumer<Exception> checkException,
                final Connection connection) {

            this.signal = signal;
            this.autoAckTarget = autoAckTarget;
            this.ackSizeQuota = ackSizeQuota;
            this.resultFuture = resultFuture;
            this.checkException = checkException;
            this.connection = connection;
        }

        @Override
        public void onCompletion(final RecordMetadata metadata, final Exception exception) {
            if (exception != null) {
                resultFuture.completeExceptionally(exception);
                checkException.accept(exception);
            } else {
                resultFuture.complete(ackFromMetadata(metadata));
            }
        }

        private Acknowledgement ackFromMetadata(@Nullable final RecordMetadata metadata) {
            final ThingId id = ThingId.of(signal.getEntityId());
            final AcknowledgementLabel label = getAcknowledgementLabel(autoAckTarget).orElse(NO_ACK_LABEL);
            if (metadata == null) {
                return Acknowledgement.of(label, id, HttpStatusCode.NO_CONTENT, signal.getDittoHeaders());
            } else {
                return Acknowledgement.of(label, id, HttpStatusCode.OK, signal.getDittoHeaders(), toPayload(metadata));
            }
        }

        private JsonObject toPayload(final RecordMetadata metadata) {
            final JsonObjectBuilder builder = JsonObject.newBuilder();
            currentQuota = ackSizeQuota;
            if (metadata.hasTimestamp()) {
                builder.set("timestamp", metadata.timestamp(), this::isQuotaSufficient);
            }
            builder.set("serializedKeySize", metadata.serializedKeySize(), this::isQuotaSufficient);
            builder.set("serializedValueSize", metadata.serializedValueSize(), this::isQuotaSufficient);
            if (isDebugEnabled()) {
                builder.set("topic", metadata.topic(), this::isQuotaSufficient);
                builder.set("partition", metadata.partition(), this::isQuotaSufficient);
                if (metadata.hasOffset()) {
                    builder.set("offset", metadata.offset(), this::isQuotaSufficient);
                }
            }
            return builder.build();
        }

        private boolean isQuotaSufficient(final JsonField field) {
            final int fieldSize = field.getKey().length() +
                    (field.getValue().isString() ? field.getValue().asString().length() : 8);
            if (fieldSize <= currentQuota) {
                currentQuota -= fieldSize;
                return true;
            } else {
                return false;
            }
        }

        private boolean isDebugEnabled() {
            final Map<String, String> specificConfig = connection.getSpecificConfig();
            return Boolean.parseBoolean(specificConfig.get("debug-enabled"));
        }

    }

}
