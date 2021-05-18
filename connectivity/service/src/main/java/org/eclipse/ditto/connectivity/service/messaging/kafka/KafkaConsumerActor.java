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

import java.time.Duration;
import java.util.Map;

import org.apache.kafka.streams.errors.StreamsException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.Enforcement;
import org.eclipse.ditto.connectivity.model.EnforcementFilterFactory;
import org.eclipse.ditto.connectivity.model.ResourceStatus;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.service.messaging.BaseConsumerActor;
import org.eclipse.ditto.connectivity.service.messaging.internal.RetrieveAddressStatus;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLoggingAdapter;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.kafka.Subscriptions;
import akka.kafka.javadsl.Consumer;
import akka.stream.Materializer;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.RunnableGraph;
import akka.stream.javadsl.Sink;
import scala.util.Either;

final class KafkaConsumerActor extends BaseConsumerActor {

    static final String ACTOR_NAME_PREFIX = "kafkaConsumer-";

    private final ThreadSafeDittoLoggingAdapter log;
    private final KafkaConsumerStream kafkaStream;

    @SuppressWarnings("unused")
    private KafkaConsumerActor(final Connection connection,
            final KafkaConnectionFactory factory,
            final String sourceAddress, final ActorRef inboundMappingProcessor,
            final Source source, final boolean dryRun) {
        super(connection, sourceAddress, inboundMappingProcessor, source);

        log = DittoLoggerFactory.getThreadSafeDittoLoggingAdapter(this);

        final Enforcement enforcement = source.getEnforcement().orElse(null);
        final EnforcementFilterFactory<Map<String, String>, Signal<?>> headerEnforcementFilterFactory =
                enforcement != null
                        ? newEnforcementFilterFactory(enforcement, newHeadersPlaceholder())
                        : input -> null;
        final KafkaMessageTransformer kafkaMessageTransformer =
                new KafkaMessageTransformer(source, sourceAddress, headerEnforcementFilterFactory, inboundMonitor);
        kafkaStream = new KafkaConsumerStream(factory, kafkaMessageTransformer, dryRun,
                Materializer.createMaterializer(this::getContext));
    }

    static Props props(final Connection connection, final KafkaConnectionFactory factory, final String sourceAddress,
            final ActorRef inboundMappingProcess, final Source source, final boolean dryRun) {
        return Props.create(KafkaConsumerActor.class, connection, factory, sourceAddress, inboundMappingProcess,
                source, dryRun);
    }

    @Override
    public void preStart() throws IllegalStateException, StreamsException {
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

    private class KafkaConsumerStream {

        private final RunnableGraph<Consumer.Control> runnableKafkaStream;
        private final Materializer materializer;
        private Consumer.Control kafkaStream;

        private KafkaConsumerStream(final KafkaConnectionFactory factory,
                final KafkaMessageTransformer kafkaMessageTransformer,
                final boolean dryRun, //TODO: handle dry run
                final Materializer materializer) {

            this.materializer = materializer;
            runnableKafkaStream = Consumer.plainSource(null, Subscriptions.topics(
                    sourceAddress)) //TODO: replace "null" with actual ConsumerSettings. See https://akka.io/alpakka-samples/kafka-to-websocket-clients/example.html#subscribe-to-the-kafka-topic for an example
                    .throttle(100, Duration.ofSeconds(1)) //TODO: make this configurable
                    .filter((consumerRecord) -> consumerRecord.value() != null)
                    .map(kafkaMessageTransformer::transform) //TODO: ensure serialisation works correctly. The record is now of type <Object,Object> and no longer <String,String>
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

        private void forwardExternalMessage(final ExternalMessage value) {
            inboundMonitor.success(value);
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
                            "Please contact the service team", either
            ));
        }

        private void start() throws IllegalStateException, StreamsException {
            if (kafkaStream != null) {
                stop();
            }
            kafkaStream = runnableKafkaStream.run(materializer);
        }

        private void stop() {
            if (kafkaStream != null) {
                kafkaStream.shutdown();
                kafkaStream = null;
            }
        }

    }

}
