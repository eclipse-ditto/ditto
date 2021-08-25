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

import java.time.Instant;
import java.util.concurrent.CompletionException;

import javax.annotation.Nullable;

import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.ConnectivityStatus;
import org.eclipse.ditto.connectivity.model.ResourceStatus;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.service.messaging.BaseConsumerActor;
import org.eclipse.ditto.connectivity.service.messaging.ConnectivityStatusResolver;
import org.eclipse.ditto.connectivity.service.messaging.internal.ConnectionFailure;
import org.eclipse.ditto.connectivity.service.messaging.internal.RetrieveAddressStatus;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.internal.utils.config.InstanceIdentifierSupplier;

import akka.Done;
import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.stream.Materializer;
import akka.stream.javadsl.Sink;

/**
 * Actor which streams messages from Kafka.
 */
final class KafkaConsumerActor extends BaseConsumerActor {

    static final String ACTOR_NAME_PREFIX = "kafkaConsumer-";

    private final ThreadSafeDittoLoggingAdapter log;
    private final KafkaConsumerStream kafkaStream;

    @SuppressWarnings("unused")
    private KafkaConsumerActor(final Connection connection,
            final KafkaConsumerStreamFactory streamFactory,
            final String sourceAddress,
            final Source source,
            final Sink<Object, NotUsed> inboundMappingSink,
            final ConnectivityStatusResolver connectivityStatusResolver) {
        super(connection, sourceAddress, inboundMappingSink, source, connectivityStatusResolver);

        log = DittoLoggerFactory.getThreadSafeDittoLoggingAdapter(this);
        final Materializer materializer = Materializer.createMaterializer(this::getContext);
        final Integer qos = source.getQos().orElse(0);
        if (qos.equals(1)) {
            kafkaStream = streamFactory.newAtLeastOnceConsumerStream(materializer, inboundMonitor,
                    getMessageMappingSink(), getDittoRuntimeExceptionSink());
        } else {
            kafkaStream = streamFactory.newAtMostOnceConsumerStream(materializer, inboundMonitor,
                    getMessageMappingSink(), getDittoRuntimeExceptionSink());
        }
    }

    static Props props(final Connection connection,
            final KafkaConsumerStreamFactory streamFactory,
            final String sourceAddress,
            final Source source,
            final Sink<Object, NotUsed> inboundMappingSink,
            final ConnectivityStatusResolver connectivityStatusResolver) {
        return Props.create(KafkaConsumerActor.class, connection, streamFactory, sourceAddress, source,
                inboundMappingSink, connectivityStatusResolver);
    }

    @Override
    public void preStart() throws IllegalStateException {
        kafkaStream.start().whenComplete(this::handleStreamCompletion);
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
                .match(MessageRejectedException.class, this::escalateRejection)
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

    private void handleStreamCompletion(@Nullable final Done done, @Nullable final Throwable throwable) {
        final ConnectivityStatus status;
        final ResourceStatus statusUpdate;
        final Instant now = Instant.now();
        if (null == throwable) {
            status = ConnectivityStatus.CLOSED;
            statusUpdate = ConnectivityModelFactory.newStatusUpdate(
                    InstanceIdentifierSupplier.getInstance().get(),
                    status,
                    sourceAddress,
                    "Consumer closed", now);
        } else if (throwable instanceof CompletionException &&
                throwable.getCause() instanceof MessageRejectedException) {
            final MessageRejectedException cause = (MessageRejectedException) throwable.getCause();
            inboundMonitor.exception(cause);
            status = ConnectivityStatus.CLOSED;
            self().tell(cause, ActorRef.noSender());
            statusUpdate = ConnectivityModelFactory.newStatusUpdate(
                    InstanceIdentifierSupplier.getInstance().get(),
                    status,
                    sourceAddress,
                    "Consumer closed", now);
        } else {
            log.debug("Consumer failed with error! <{}: {}>", throwable.getClass().getSimpleName(),
                    throwable.getMessage());
            status = connectivityStatusResolver.resolve(throwable);
            escalate(throwable, "Unexpected consumer failure.");
            statusUpdate = ConnectivityModelFactory.newStatusUpdate(
                    InstanceIdentifierSupplier.getInstance().get(),
                    status,
                    sourceAddress,
                    ConnectionFailure.determineFailureDescription(now, throwable,
                            "Kafka consumer failed."), now);
        }
        handleAddressStatus(statusUpdate);
    }

    private void escalateRejection(final MessageRejectedException ex) {
        throw ex;
    }

    private void escalate(final Throwable throwable, final String description) {
        getContext().getParent()
                .tell(ConnectionFailure.of(getSelf(), throwable, description), getSelf());
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

}
