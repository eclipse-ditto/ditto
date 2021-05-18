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
import java.util.function.Supplier;

import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Branched;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KeyValueMapper;
import org.apache.kafka.streams.kstream.Named;
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

        final Enforcement enforcement = source.getEnforcement().orElse(null);
        final EnforcementFilterFactory<Map<String, String>, Signal<?>> headerEnforcementFilterFactory =
                enforcement != null
                        ? newEnforcementFilterFactory(enforcement, newHeadersPlaceholder())
                        : input -> null;
        final Supplier<KafkaMessageTransformer> kafkaMessageTransformerFactory =
                () -> new KafkaMessageTransformer(source, sourceAddress, headerEnforcementFilterFactory,
                        inboundMonitor);
        kafkaStream = new KafkaConsumerStream(factory, kafkaMessageTransformerFactory, dryRun);
        log = DittoLoggerFactory.getThreadSafeDittoLoggingAdapter(this);
    }

    private static boolean isExternalMessage(final String key, final Object value) {
        return value instanceof ExternalMessage;
    }

    private static boolean isDittoRuntimeException(final String key, final Object value) {
        return value instanceof DittoRuntimeException;
    }

    static Props props(final Connection connection, final KafkaConnectionFactory factory, final String sourceAddress,
            final ActorRef inboundMappingProcess, final Source source, final boolean dryRun) {
        return Props.create(KafkaConsumerActor.class, connection, factory, sourceAddress, inboundMappingProcess,
                source, dryRun);
    }

    @Override
    public void preStart() throws IllegalStateException, org.apache.kafka.streams.errors.StreamsException {
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

        private static final String BRANCH_PREFIX = "TransformationResult-";
        private static final String MESSAGE_TRANSFORMER = "messageTransformer";
        private static final String ERROR_FORWARDER = "errorForwarder";
        private static final String MESSAGE_FORWARDER = "messageForwarder";
        private static final String MESSAGE_DROPPER = "messageDropper";
        private final Duration closeTimeout = Duration.ofSeconds(10);
        private final Supplier<KafkaStreams> kafkaStreamsSupplier;
        private KafkaStreams kafkaStreams;


        private KafkaConsumerStream(final KafkaConnectionFactory factory,
                final Supplier<KafkaMessageTransformer> kafkaMessageTransformerFactory,
                final boolean dryRun) {
            final StreamsBuilder streamsBuilder = new StreamsBuilder();
            if (!dryRun) {
                // TODO: kafka source - Implement rate limiting/throttling
                final Map<String, KStream<String, Object>> branches =
                        streamsBuilder.<String, String>stream(sourceAddress)
                                .filter((key, value) -> key != null && value != null)
                                .transform(kafkaMessageTransformerFactory::get, Named.as(MESSAGE_TRANSFORMER))
                                .split(Named.as(BRANCH_PREFIX))
                                .branch(KafkaConsumerActor::isExternalMessage, Branched.as(MESSAGE_FORWARDER))
                                .branch(KafkaConsumerActor::isDittoRuntimeException, Branched.as(ERROR_FORWARDER))
                                .defaultBranch(Branched.as(MESSAGE_DROPPER));

                branches.get(BRANCH_PREFIX + MESSAGE_FORWARDER)
                        .map((KeyValueMapper<String, Object, KeyValue<String, ExternalMessage>>)
                                (key, value) -> new KeyValue<>(key, (ExternalMessage) value))
                        .foreach((key, value) -> {
                            inboundMonitor.success(value);
                            forwardToMappingActor(value,
                                    () -> {
                                        // TODO: kafka source - Implement acks
                                    },
                                    redeliver -> {
                                        // TODO: kafka source - Implement acks
                                    });
                        });

                branches.get(BRANCH_PREFIX + ERROR_FORWARDER)
                        .map((KeyValueMapper<String, Object, KeyValue<String, DittoRuntimeException>>)
                                (key, value) -> new KeyValue<>(key, (DittoRuntimeException) value))
                        .foreach((key, value) -> {
                            inboundMonitor.failure(value.getDittoHeaders(), value);
                            forwardToMappingActor(value);
                        });

                branches.get(BRANCH_PREFIX + MESSAGE_DROPPER).foreach((key, value) -> inboundMonitor.exception(
                        "Got unexpected message <{0}>. This is an internal error. Please contact the service team",
                        value
                ));

            }
            kafkaStreamsSupplier = () -> new KafkaStreams(streamsBuilder.build(), factory.consumerStreamProperties());
        }

        private void start() throws IllegalStateException, org.apache.kafka.streams.errors.StreamsException {
            kafkaStreams = kafkaStreamsSupplier.get();
            kafkaStreams.start();
        }

        private void stop() {
            if (kafkaStreams != null) {
                kafkaStreams.close(closeTimeout);
                kafkaStreams.cleanUp();
                kafkaStreams = null;
            }
        }

    }

}
