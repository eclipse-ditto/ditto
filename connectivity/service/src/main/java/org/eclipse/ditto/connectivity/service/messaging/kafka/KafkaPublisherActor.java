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
package org.eclipse.ditto.connectivity.service.messaging.kafka;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.errors.RetriableException;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.common.ByteBufferUtils;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.api.OutboundSignal;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.GenericTarget;
import org.eclipse.ditto.connectivity.model.MessageSendingFailedException;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.config.KafkaProducerConfig;
import org.eclipse.ditto.connectivity.service.messaging.BasePublisherActor;
import org.eclipse.ditto.connectivity.service.messaging.ConnectivityStatusResolver;
import org.eclipse.ditto.connectivity.service.messaging.ExceptionToAcknowledgementConverter;
import org.eclipse.ditto.connectivity.service.messaging.SendResult;
import org.eclipse.ditto.connectivity.service.messaging.internal.ConnectionFailure;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;

import akka.Done;
import akka.NotUsed;
import akka.actor.Props;
import akka.actor.Status;
import akka.japi.Pair;
import akka.japi.pf.ReceiveBuilder;
import akka.kafka.ProducerMessage;
import akka.kafka.javadsl.SendProducer;
import akka.stream.KillSwitch;
import akka.stream.KillSwitches;
import akka.stream.Materializer;
import akka.stream.OverflowStrategy;
import akka.stream.QueueOfferResult;
import akka.stream.RestartSettings;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.RestartFlow;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.javadsl.SourceQueueWithComplete;

/**
 * Responsible for publishing {@link org.eclipse.ditto.connectivity.api.ExternalMessage}s into a Kafka broker.
 */
final class KafkaPublisherActor extends BasePublisherActor<KafkaPublishTarget> {

    static final String ACTOR_NAME = "kafkaPublisher";

    private final boolean dryRun;
    private final KafkaProducerStream producerStream;

    @SuppressWarnings("unused")
    private KafkaPublisherActor(final Connection connection,
            final SendProducerFactory producerFactory,
            final boolean dryRun,
            final ConnectivityStatusResolver connectivityStatusResolver,
            final ConnectivityConfig connectivityConfig) {

        super(connection, connectivityStatusResolver, connectivityConfig);
        this.dryRun = dryRun;
        final var connectionConfig = connectivityConfig.getConnectionConfig();
        final var kafkaConfig = connectionConfig.getKafkaConfig();
        producerStream = new KafkaProducerStream(kafkaConfig.getProducerConfig(),
                Materializer.createMaterializer(this::getContext),
                producerFactory);
    }

    /**
     * Creates Akka configuration object {@link akka.actor.Props} for this {@code BasePublisherActor}.
     *
     * @param connection the connection this publisher belongs to.
     * @param producerFactory factory to create kafka SendProducer.
     * @param dryRun whether this publisher is only created for a test or not.
     * @param connectivityStatusResolver connectivity status resolver to resolve occurred exceptions to a connectivity
     * status.
     * @param connectivityConfig the config of the connectivity service with potential overwrites.
     * @return the Akka configuration Props object.
     */
    static Props props(final Connection connection,
            final SendProducerFactory producerFactory,
            final boolean dryRun,
            final ConnectivityStatusResolver connectivityStatusResolver,
            final ConnectivityConfig connectivityConfig) {

        return Props.create(KafkaPublisherActor.class,
                connection,
                producerFactory,
                dryRun,
                connectivityStatusResolver,
                connectivityConfig);
    }

    @Override
    protected ExceptionToAcknowledgementConverter getExceptionConverter() {
        return KafkaExceptionConverter.INSTANCE;
    }

    @Override
    protected void preEnhancement(final ReceiveBuilder receiveBuilder) {
        receiveBuilder
                .match(OutboundSignal.Mapped.class, this::isDryRun, outbound ->
                        logger.withCorrelationId(outbound.getSource())
                                .info("Message dropped in dry run mode: {}", outbound))
                .matchEquals(GracefulStop.INSTANCE, unused -> this.stopGracefully());
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();
        reportInitialConnectionState();
    }

    @Override
    protected void postEnhancement(final ReceiveBuilder receiveBuilder) {
        // noop
    }

    @Override
    protected KafkaPublishTarget toPublishTarget(final GenericTarget target) {
        return KafkaPublishTarget.fromTargetAddress(target.getAddress());
    }

    @Override
    protected CompletionStage<SendResult> publishMessage(final Signal<?> signal,
            @Nullable final Target autoAckTarget,
            final KafkaPublishTarget publishTarget,
            final ExternalMessage message,
            final int maxTotalMessageSize,
            final int ackSizeQuota,
            @Nullable final AuthorizationContext targetAuthorizationContext) {

        @Nullable final AcknowledgementLabel autoAckLabel = getAcknowledgementLabel(autoAckTarget).orElse(null);
        final Function<RecordMetadata, SendResult> callback =
                new ProducerCallback(signal, autoAckLabel, ackSizeQuota, connection);

        final ExternalMessage messageWithConnectionIdHeader =
                message.withHeader("ditto-connection-id", connection.getId().toString());

        return producerStream.publish(publishTarget, messageWithConnectionIdHeader).thenApply(callback);
    }

    @Override
    public void postStop() throws Exception {
        super.postStop();
        producerStream.shutdown();
    }

    private boolean isDryRun() {
        return dryRun;
    }

    private void reportInitialConnectionState() {
        logger.info("Publisher ready.");
        getContext().getParent().tell(new Status.Success(Done.done()), getSelf());
    }

    private void stopGracefully() {
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

    private static final class ProducerCallback implements Function<RecordMetadata, SendResult> {

        private final Signal<?> signal;
        private final int ackSizeQuota;
        private int currentQuota;
        private final Connection connection;
        @Nullable private final AcknowledgementLabel autoAckLabel;

        private ProducerCallback(final Signal<?> signal,
                @Nullable final AcknowledgementLabel autoAckLabel,
                final int ackSizeQuota,
                final Connection connection) {

            this.signal = signal;
            this.autoAckLabel = autoAckLabel;
            this.ackSizeQuota = ackSizeQuota;
            this.connection = connection;
        }

        @Override
        public SendResult apply(final RecordMetadata recordMetadata) {
            return buildResponseFromMetadata(recordMetadata);
        }


        private SendResult buildResponseFromMetadata(@Nullable final RecordMetadata metadata) {
            final DittoHeaders dittoHeaders = signal.getDittoHeaders();
            final boolean verbose = isDebugEnabled() && metadata != null;
            final JsonObject ackPayload = verbose ? toPayload(metadata) : null;
            final HttpStatus httpStatus = verbose ? HttpStatus.OK : HttpStatus.NO_CONTENT;
            final Optional<EntityId> entityIdOptional =
                    WithEntityId.getEntityIdOfType(EntityId.class, signal);
            @Nullable final Acknowledgement issuedAck;
            if (entityIdOptional.isPresent() && null != autoAckLabel) {
                issuedAck = Acknowledgement.of(autoAckLabel,
                        entityIdOptional.get(),
                        httpStatus,
                        dittoHeaders,
                        ackPayload);
            } else {
                issuedAck = null;
            }

            return new SendResult(issuedAck, dittoHeaders);
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

        private KafkaExceptionConverter() {}

        @Override
        protected HttpStatus getHttpStatusForGenericException(final Throwable exception) {
            // return 500 for retryable exceptions -> sender will retry
            // return 400 for non-retryable exceptions -> sender will give up
            return exception instanceof RetriableException
                    ? HttpStatus.INTERNAL_SERVER_ERROR
                    : HttpStatus.BAD_REQUEST;
        }

    }

    final class KafkaProducerStream {

        private static final String TOO_MANY_IN_FLIGHT_MESSAGE_DESCRIPTION = """ 
                This can have the following reasons:
                 a) The Kafka consumer does not consume the messages fast enough.
                 b) The client count of this connection is not configured high enough.""";

        private final KillSwitch killSwitch;
        private final SourceQueueWithComplete<ProducerMessage.Envelope<String, ByteBuffer, CompletableFuture<RecordMetadata>>>
                sourceQueue;
        private final AtomicReference<SendProducer<String, ByteBuffer>> sendProducer = new AtomicReference<>();

        private KafkaProducerStream(final KafkaProducerConfig config, final Materializer materializer,
                final SendProducerFactory producerFactory) {

            final RestartSettings restartSettings =
                    RestartSettings.create(config.getMinBackoff(), config.getMaxBackoff(), config.getRandomFactor())
                            .withMaxRestarts(config.getMaxRestartsCount(), config.getMaxRestartsWithin());

            final Pair<SourceQueueWithComplete<ProducerMessage.Envelope<String, ByteBuffer, CompletableFuture<RecordMetadata>>>, Source<ProducerMessage.Envelope<String, ByteBuffer, CompletableFuture<RecordMetadata>>, NotUsed>>
                    sourcePair =
                    Source.<ProducerMessage.Envelope<String, ByteBuffer, CompletableFuture<RecordMetadata>>>
                            queue(config.getQueueSize(), OverflowStrategy.dropNew()).preMaterialize(materializer);

            sourceQueue = sourcePair.first();
            killSwitch = sourcePair.second()
                    .via(RestartFlow.onFailuresWithBackoff(restartSettings, () -> {
                        logger.debug("Creating new kafka publish flow.");
                        Optional.ofNullable(sendProducer.getAndSet(producerFactory.newSendProducer()))
                                .ifPresent(SendProducer::close);
                        return Flow.fromFunction(envelope -> sendProducer.get()
                                .sendEnvelope(envelope)
                                .whenComplete((results, exception) -> handleSendResult(results,
                                        exception,
                                        envelope.passThrough())));
                    }))
                    .viaMat(KillSwitches.single(), Keep.right())
                    .toMat(Sink.ignore(), Keep.left())
                    .run(materializer);
        }

        private void handleSendResult(
                @Nullable final ProducerMessage.Results<String, ByteBuffer, CompletableFuture<RecordMetadata>> results,
                @Nullable final Throwable exception, final CompletableFuture<RecordMetadata> resultFuture) {
            if (exception == null) {
                if (results instanceof ProducerMessage.Result) {
                    final ProducerMessage.Result<String, ByteBuffer, CompletableFuture<RecordMetadata>> result =
                            (ProducerMessage.Result<String, ByteBuffer, CompletableFuture<RecordMetadata>>) results;
                    resultFuture.complete(result.metadata());
                } else {
                    // should never happen, we provide only ProducerMessage.single to the source
                    logger.warning("Received multipart result, ignoring: {}", results);
                    resultFuture.completeExceptionally(
                            new IllegalArgumentException("Received unexpected multipart result."));
                }
            } else {
                logger.debug("Failed to send kafka record: [{}] {}", exception.getClass().getName(),
                        exception.getMessage());
                resultFuture.completeExceptionally(exception);
                escalate(exception, ConnectionFailure.determineFailureDescription(Instant.now(),
                        exception, "Broker may not be available."));
            }
        }

        private CompletableFuture<RecordMetadata> publish(final KafkaPublishTarget publishTarget,
                final ExternalMessage externalMessage) {

            final CompletableFuture<RecordMetadata> resultFuture = new CompletableFuture<>();
            final ProducerRecord<String, ByteBuffer> producerRecord = getProducerRecord(publishTarget, externalMessage);
            final ProducerMessage.Envelope<String, ByteBuffer, CompletableFuture<RecordMetadata>> envelope =
                    ProducerMessage.single(producerRecord, resultFuture);
            if (null != sourceQueue) {
                sourceQueue.offer(envelope).whenComplete(handleQueueOfferResult(externalMessage, resultFuture));
            } else {
                final IllegalStateException ex = new IllegalStateException("Publisher not initialized");
                logger.error(ex, ex.getMessage());
                resultFuture.completeExceptionally(ex);
            }

            return resultFuture;
        }

        private ProducerRecord<String, ByteBuffer> getProducerRecord(final KafkaPublishTarget publishTarget,
                final ExternalMessage externalMessage) {

            final ByteBuffer payload = mapExternalMessagePayload(externalMessage);
            final Iterable<Header> headers = mapExternalMessageHeaders(externalMessage);

            return new ProducerRecord<>(publishTarget.getTopic(),
                    publishTarget.getPartition().orElse(null),
                    publishTarget.getKey().orElse(null),
                    payload, headers);
        }

        private Iterable<Header> mapExternalMessageHeaders(final ExternalMessage externalMessage) {
            return externalMessage.getHeaders()
                    .entrySet()
                    .stream()
                    .map(header -> new RecordHeader(header.getKey(),
                            header.getValue().getBytes(StandardCharsets.UTF_8)))
                    .collect(Collectors.toList());
        }

        private ByteBuffer mapExternalMessagePayload(final ExternalMessage externalMessage) {
            if (externalMessage.isBytesMessage()) {
                return externalMessage.getBytePayload()
                        .orElseGet(ByteBufferUtils::empty);
            } else if (externalMessage.isTextMessage()) {
                final Charset charset = getCharsetFromMessage(externalMessage);
                return externalMessage.getTextPayload()
                        .map(text -> text.getBytes(charset))
                        .map(ByteBuffer::wrap)
                        .orElseGet(ByteBufferUtils::empty);
            } else {
                return ByteBufferUtils.empty();
            }
        }

        // Async callback. Must be thread-safe.
        private BiConsumer<QueueOfferResult, Throwable> handleQueueOfferResult(final ExternalMessage message,
                final CompletableFuture<?> resultFuture) {

            return (queueOfferResult, error) -> {
                if (error != null) {
                    final String errorDescription = "Source queue failure";
                    logger.error(error, errorDescription);
                    resultFuture.completeExceptionally(error);
                    escalate(error, errorDescription);
                } else if (Objects.equals(queueOfferResult, QueueOfferResult.dropped())) {
                    resultFuture.completeExceptionally(MessageSendingFailedException.newBuilder()
                            .message("Outgoing Kafka message dropped: There are too many uncommitted messages.")
                            .description(TOO_MANY_IN_FLIGHT_MESSAGE_DESCRIPTION)
                            .dittoHeaders(message.getInternalHeaders())
                            .build());
                }
            };
        }

        private void shutdown() {
            Optional.ofNullable(sendProducer.get()).ifPresent(SendProducer::close);
            killSwitch.shutdown();
        }

    }

}
