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
import org.apache.kafka.common.errors.RetriableException;
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
import org.eclipse.ditto.services.connectivity.messaging.ExceptionToAcknowledgementConverter;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.OutboundSignal;
import org.eclipse.ditto.signals.acks.base.Acknowledgement;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.base.CommandResponse;

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

    static final String ACTOR_NAME = "kafkaPublisher";

    private final KafkaConnectionFactory connectionFactory;
    private final boolean dryRun;

    private Producer<String, String> producer;

    @SuppressWarnings("unused")
    private KafkaPublisherActor(final Connection connection, final KafkaConnectionFactory factory,
            final boolean dryRun) {

        super(connection);
        this.dryRun = dryRun;
        connectionFactory = factory;

        startInternalKafkaProducer();
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
    protected ExceptionToAcknowledgementConverter getExceptionConverter() {
        return KafkaExceptionConverter.INSTANCE;
    }

    @Override
    protected void preEnhancement(final ReceiveBuilder receiveBuilder) {
        receiveBuilder.match(OutboundSignal.Mapped.class, this::isDryRun,
                outbound -> logger.withCorrelationId(outbound.getSource())
                        .info("Message dropped in dry run mode: {}", outbound))
                .matchEquals(GracefulStop.INSTANCE, unused -> this.stopGracefully());
    }

    @Override
    protected boolean shouldPublishAcknowledgement(final Acknowledgement acknowledgement) {
        return !NO_ACK_LABEL.equals(acknowledgement.getLabel());
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
    protected CompletionStage<CommandResponse<?>> publishMessage(final Signal<?> signal,
            @Nullable final Target autoAckTarget,
            final KafkaPublishTarget publishTarget,
            final ExternalMessage message,
            final int maxTotalMessageSize,
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
            final CompletableFuture<CommandResponse<?>> resultFuture = new CompletableFuture<>();
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
        if (!(exception instanceof RetriableException)) {
            escalate(exception, "Got non-retriable exception");
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

    private void startInternalKafkaProducer() {
        logger.info("Starting internal Kafka producer.");
        closeProducer();
        producer = connectionFactory.newProducer();
    }

    private void closeProducer() {
        if (producer != null) {
            // Give up any buffered messages and close the producer immediately.
            producer.close(0, TimeUnit.MILLISECONDS);
        }
    }

    private void stopInternalKafkaProducer() {
        logger.info("Stopping internal Kafka producer.");
        closeProducer();
    }

    private void reportInitialConnectionState() {
        logger.info("Publisher ready.");
        getContext().getParent().tell(new Status.Success(Done.done()), getSelf());
    }

    private void stopGracefully() {
        stopInternalKafkaProducer();
        logger.debug("Stopping myself.");
        getContext().stop(getSelf());
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
        private final CompletableFuture<CommandResponse<?>> resultFuture;
        private final Consumer<Exception> checkException;
        private int currentQuota;
        private final Connection connection;

        private ProducerCallBack(final Signal<?> signal,
                @Nullable final Target autoAckTarget,
                final int ackSizeQuota,
                final CompletableFuture<CommandResponse<?>> resultFuture,
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
            if (metadata == null || !isDebugEnabled()) {
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
            builder.set("topic", metadata.topic(), this::isQuotaSufficient);
            builder.set("partition", metadata.partition(), this::isQuotaSufficient);
            if (metadata.hasOffset()) {
                builder.set("offset", metadata.offset(), this::isQuotaSufficient);
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
            return Boolean.parseBoolean(specificConfig.getOrDefault("debugEnabled", Boolean.FALSE.toString()));
        }

    }

    private static final class KafkaExceptionConverter extends ExceptionToAcknowledgementConverter {

        static final ExceptionToAcknowledgementConverter INSTANCE = new KafkaExceptionConverter();

        private KafkaExceptionConverter() {
            super();
        }

        @Override
        protected HttpStatusCode getStatusCodeForGenericException(final Exception exception) {
            // return 500 for retryable exceptions -> sender will retry
            // return 400 for non-retryable exceptions -> sender will give up
            return exception instanceof RetriableException
                    ? HttpStatusCode.INTERNAL_SERVER_ERROR
                    : HttpStatusCode.BAD_REQUEST;
        }

    }

}
