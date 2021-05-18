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

import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.Record;
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
    private static final Duration CLOSE_TIMEOUT = Duration.ofSeconds(10);
    private static final String MESSAGE_TRANSFORMER = "messageTransformer";
    private static final String ERROR_FORWARDER = "errorForwarder";
    private static final String MESSAGE_FORWARDER = "messageForwarder";

    private final ThreadSafeDittoLoggingAdapter log;
    private final KafkaStreams kafkaStreams;

    @SuppressWarnings("unused")
    private KafkaConsumerActor(final Connection connection,
            final KafkaConnectionFactory factory,
            final String sourceAddress, final ActorRef inboundMappingProcessor,
            final Source source, final boolean dryRun) {
        super(connection, sourceAddress, inboundMappingProcessor, source);
        log = DittoLoggerFactory.getThreadSafeDittoLoggingAdapter(this);
        kafkaStreams = buildKafkaStream(factory, dryRun);
        kafkaStreams.start();
    }

    private KafkaStreams buildKafkaStream(final KafkaConnectionFactory factory, final boolean dryRun) {
        final Enforcement enforcement = source.getEnforcement().orElse(null);
        final EnforcementFilterFactory<Map<String, String>, Signal<?>> headerEnforcementFilterFactory =
                enforcement != null
                        ? newEnforcementFilterFactory(enforcement, newHeadersPlaceholder())
                        : input -> null;
        final StreamsBuilder streamsBuilder = new StreamsBuilder();
        if (!dryRun) {
            // TODO: kafka source - Implement rate limiting/throttling
            streamsBuilder.<String, String>stream(sourceAddress)
                    .filter((key, value) -> key != null && value != null)
                    .process(() -> new IncomingMessageHandler(source, sourceAddress,
                            headerEnforcementFilterFactory, inboundMonitor, MESSAGE_FORWARDER, ERROR_FORWARDER
                    ), Named.as(MESSAGE_TRANSFORMER));
        }
        final Topology topology = streamsBuilder.build();
        topology.addProcessor(ErrorForwarder.NAME, ErrorForwarder::new, MESSAGE_TRANSFORMER);
        topology.addProcessor(MessageForwarder.NAME, MessageForwarder::new, MESSAGE_TRANSFORMER);
        return new KafkaStreams(topology, factory.consumerStreamProperties());
    }

    static Props props(final Connection connection, final KafkaConnectionFactory factory, final String sourceAddress,
            final ActorRef inboundMappingProcess, final Source source, final boolean dryRun) {
        return Props.create(KafkaConsumerActor.class, connection, factory, sourceAddress, inboundMappingProcess,
                source, dryRun);
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
        if (kafkaStreams != null) {
            kafkaStreams.close(CLOSE_TIMEOUT);
            kafkaStreams.cleanUp();
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

    private class ErrorForwarder implements Processor<String, DittoRuntimeException, String, DittoRuntimeException> {

        private static final String NAME = ERROR_FORWARDER;

        @Override
        public void process(final Record<String, DittoRuntimeException> record) {
            forwardToMappingActor(record.value());
        }

    }

    private class MessageForwarder implements Processor<String, ExternalMessage, String, ExternalMessage> {

        private static final String NAME = MESSAGE_FORWARDER;

        @Override
        public void process(final Record<String, ExternalMessage> record) {
            forwardToMappingActor(record.value(),
                    () -> {
                        // TODO: kafka source - Implement acks
                    },
                    redeliver -> {
                        // TODO: kafka source - Implement acks
                    });
        }

    }

}
