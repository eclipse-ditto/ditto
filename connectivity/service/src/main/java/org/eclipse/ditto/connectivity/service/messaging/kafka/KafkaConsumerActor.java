/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import static org.eclipse.ditto.connectivity.api.EnforcementFactoryFactory.newEnforcementFilterFactory;
import static org.eclipse.ditto.internal.models.placeholders.PlaceholderFactory.newHeadersPlaceholder;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.ConnectivityStatus;
import org.eclipse.ditto.connectivity.model.Enforcement;
import org.eclipse.ditto.connectivity.model.EnforcementFilterFactory;
import org.eclipse.ditto.connectivity.model.ResourceStatus;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.service.config.KafkaConsumerConfig;
import org.eclipse.ditto.connectivity.service.messaging.BaseConsumerActor;
import org.eclipse.ditto.connectivity.service.messaging.internal.RetrieveAddressStatus;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.internal.utils.config.InstanceIdentifierSupplier;

import akka.Done;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.kafka.ConsumerSettings;
import akka.kafka.Subscriptions;
import akka.kafka.javadsl.Consumer;
import akka.stream.Materializer;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.RunnableGraph;
import akka.stream.javadsl.Sink;
import scala.util.Either;

/**
 * Actor which streams messages from Kafka.
 */
final class KafkaConsumerActor extends BaseConsumerActor {

    private static final String TTL = "ttl";
    private static final String CREATION_TIME = "creation-time";
    static final String ACTOR_NAME_PREFIX = "kafkaConsumer-";

    private final ThreadSafeDittoLoggingAdapter log;
    private final KafkaConsumerStream kafkaStream;

    @SuppressWarnings("unused")
    private KafkaConsumerActor(final Connection connection,
            final KafkaConsumerConfig kafkaConfig, final PropertiesFactory propertiesFactory,
            final String sourceAddress, final ActorRef inboundMappingProcessor,
            final Source source, final boolean dryRun) {
        super(connection, sourceAddress, inboundMappingProcessor, source);

        log = DittoLoggerFactory.getThreadSafeDittoLoggingAdapter(this);

        final ConsumerSettings<String, String> consumerSettings = propertiesFactory.getConsumerSettings(dryRun);
        final Enforcement enforcement = source.getEnforcement().orElse(null);
        final EnforcementFilterFactory<Map<String, String>, Signal<?>> headerEnforcementFilterFactory =
                enforcement != null
                        ? newEnforcementFilterFactory(enforcement, newHeadersPlaceholder())
                        : input -> null;
        final KafkaMessageTransformer kafkaMessageTransformer =
                new KafkaMessageTransformer(source, sourceAddress, headerEnforcementFilterFactory, inboundMonitor);
        kafkaStream = new KafkaConsumerStream(kafkaConfig, consumerSettings, kafkaMessageTransformer, dryRun,
                Materializer.createMaterializer(this::getContext));
    }

    static Props props(final Connection connection, final KafkaConsumerConfig kafkaConfig,
            final PropertiesFactory factory,
            final String sourceAddress, final ActorRef inboundMappingProcess, final Source source,
            final boolean dryRun) {
        return Props.create(KafkaConsumerActor.class, connection, kafkaConfig, factory, sourceAddress,
                inboundMappingProcess, source, dryRun);
    }

    @Override
    public void preStart() throws IllegalStateException {
        kafkaStream.start();
    }

    @Override
    public void postStop() throws Exception {
        super.postStop();
        shutdown();
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(ResourceStatus.class, this::handleAddressStatus)
                .match(RetrieveAddressStatus.class, ram -> getSender().tell(getCurrentSourceStatus(), getSelf()))
                .match(GracefulStop.class, stop -> this.shutdown())
                .matchAny(unhandled -> {
                    log.info("Unhandled message: {}", unhandled);
                    unhandled(unhandled);
                })
                .build();
    }

    @Override
    protected ThreadSafeDittoLoggingAdapter log() {
        return log;
    }

    private void shutdown() {
        if (kafkaStream != null) {
            kafkaStream.stop();
        }
    }

    /**
     * Message that allows gracefully stopping the consumer actor.
     */
    static final class GracefulStop {

        static final GracefulStop INSTANCE = new GracefulStop();

        private GracefulStop() {
            // intentionally empty
        }

    }

    private final class KafkaConsumerStream {

        private final RunnableGraph<Consumer.Control> runnableKafkaStream;
        private final Materializer materializer;
        @Nullable private Consumer.Control kafkaStream;

        private KafkaConsumerStream(
                final KafkaConsumerConfig kafkaConfig,
                final ConsumerSettings<String, String> consumerSettings,
                final KafkaMessageTransformer kafkaMessageTransformer,
                final boolean dryRun,
                final Materializer materializer) {

            this.materializer = materializer;
            runnableKafkaStream = Consumer.plainSource(consumerSettings, Subscriptions.topics(sourceAddress))
                    .throttle(kafkaConfig.getThrottlingConfig().getLimit(),
                            kafkaConfig.getThrottlingConfig().getInterval())
                    .filter(consumerRecord -> isNotDryRun(consumerRecord, dryRun))
                    .filter(consumerRecord -> consumerRecord.value() != null)
                    .filter(this::isNotExpired)
                    .map(kafkaMessageTransformer::transform)
                    .divertTo(this.externalMessageSink(), this::isExternalMessage)
                    .divertTo(this.dittoRuntimeExceptionSink(), this::isDittoRuntimeException)
                    .to(this.unexpectedMessageSink());
        }

        private Sink<Either<ExternalMessage, DittoRuntimeException>, ?> externalMessageSink() {
            return Flow.fromFunction(this::extractExternalMessage)
                    .to(Sink.foreach(this::forwardExternalMessage));
        }

        private boolean isExternalMessage(final Either<ExternalMessage, DittoRuntimeException> value) {
            return value.isLeft();
        }

        private ExternalMessage extractExternalMessage(final Either<ExternalMessage, DittoRuntimeException> value) {
            return value.left().get();
        }

        private boolean isNotExpired(final ConsumerRecord<String, String> consumerRecord) {
            final Headers headers = consumerRecord.headers();
            final long now = Instant.now().toEpochMilli();
            try {
                final Optional<Long> creationTimeOptional = Optional.ofNullable(headers.lastHeader(CREATION_TIME))
                        .map(Header::value)
                        .map(String::new)
                        .map(Long::parseLong);
                final Optional<Long> ttlOptional = Optional.ofNullable(headers.lastHeader(TTL))
                        .map(Header::value)
                        .map(String::new)
                        .map(Long::parseLong);
                if (creationTimeOptional.isPresent() && ttlOptional.isPresent()) {
                    return now - creationTimeOptional.get() >= ttlOptional.get();
                }
                return true;
            } catch (final Exception e) {
                // Errors during reading/parsing headers should not cause the message to be dropped.
                return true;
            }
        }

        private boolean isNotDryRun(final ConsumerRecord<String, String> record, final boolean dryRun) {
            if (dryRun && log.isDebugEnabled()) {
                log.debug("Dropping record (key: {}, topic: {}, partition: {}, offset: {}) in dry run mode.",
                        record.key(), record.topic(), record.partition(), record.offset());
            }
            return !dryRun;
        }

        private void forwardExternalMessage(final ExternalMessage value) {
            forwardToMappingActor(value,
                    () -> {
                        // TODO: kafka source - Implement acks
                    },
                    redeliver -> {
                        // TODO: kafka source - Implement acks
                    });
        }

        private Sink<Either<ExternalMessage, DittoRuntimeException>, ?> dittoRuntimeExceptionSink() {
            return Flow.fromFunction(this::extractDittoRuntimeException)
                    .to(Sink.foreach(this::forwardDittoRuntimeException));
        }

        private boolean isDittoRuntimeException(final Either<ExternalMessage, DittoRuntimeException> value) {
            return value.isRight();
        }

        private DittoRuntimeException extractDittoRuntimeException(
                final Either<ExternalMessage, DittoRuntimeException> value) {
            return value.right().get();
        }

        private void forwardDittoRuntimeException(final DittoRuntimeException value) {
            inboundMonitor.failure(value.getDittoHeaders(), value);
            forwardToMappingActor(value);
        }

        private Sink<Either<ExternalMessage, DittoRuntimeException>, ?> unexpectedMessageSink() {
            return Sink.foreach(either -> inboundMonitor.exception(
                    "Got unexpected transformation result <{0}>. This is an internal error. " +
                            "Please contact the service team.", either
            ));
        }

        private void start() throws IllegalStateException {
            if (kafkaStream != null) {
                stop();
            }
            kafkaStream = runnableKafkaStream.run(materializer);
            kafkaStream.isShutdown().whenComplete(this::handleStreamCompletion);
        }

        private void handleStreamCompletion(@Nullable final Done done, @Nullable final Throwable throwable) {
            final ConnectivityStatus status;
            if (null == throwable) {
                status = ConnectivityStatus.CLOSED;
            } else {
                status = ConnectivityStatus.FAILED;
                if (throwable instanceof DittoRuntimeException) {
                    forwardDittoRuntimeException((DittoRuntimeException) throwable);
                } else {
                    inboundMonitor.exception("Got unexpected error on stream completion <{0}>." +
                            "This is an internal error. Please contact the service team", throwable);
                }
            }
            final ResourceStatus statusUpdate = ConnectivityModelFactory.newStatusUpdate(
                    InstanceIdentifierSupplier.getInstance().get(),
                    status,
                    sourceAddress,
                    "Consumer closed", Instant.now());
            handleAddressStatus(statusUpdate);
        }

        private void stop() {
            if (kafkaStream != null) {
                // TODO use drainAndShutdown?
                kafkaStream.shutdown();
                kafkaStream = null;
            }
        }

    }

}
