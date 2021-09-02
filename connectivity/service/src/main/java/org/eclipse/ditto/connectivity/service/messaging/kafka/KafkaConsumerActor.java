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

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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

    private static final Duration MAX_SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);
    static final String ACTOR_NAME_PREFIX = "kafkaConsumer-";
    private static final int DEFAULT_CONSUMPTION_QOS = 0;

    private final ThreadSafeDittoLoggingAdapter log;
    private RestartableKafkaConsumerStream kafkaStream;

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
        final Integer qos = source.getQos().orElse(DEFAULT_CONSUMPTION_QOS);
        if (qos.equals(1)) {
            kafkaStream = new RestartableKafkaConsumerStream(
                    () -> {
                        final KafkaConsumerStream kafkaConsumerStream =
                                streamFactory.newAtLeastOnceConsumerStream(materializer, inboundMonitor,
                                        getMessageMappingSink(), getDittoRuntimeExceptionSink());
                        kafkaConsumerStream.whenComplete(this::handleStreamCompletion);
                        return kafkaConsumerStream;
                    });
        } else {
            kafkaStream = new RestartableKafkaConsumerStream(
                    () -> {
                        final KafkaConsumerStream kafkaConsumerStream =
                                streamFactory.newAtMostOnceConsumerStream(materializer, inboundMonitor,
                                        getMessageMappingSink(), getDittoRuntimeExceptionSink());
                        kafkaConsumerStream.whenComplete(this::handleStreamCompletion);
                        return kafkaConsumerStream;
                    });
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
                .match(MessageRejectedException.class, this::restartStream)
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
            try {
                kafkaStream.stop()
                        .toCompletableFuture()
                        .orTimeout(MAX_SHUTDOWN_TIMEOUT.toSeconds(), TimeUnit.SECONDS)
                        .join();
            } catch (final CompletionException exception) {
                final Throwable cause = exception.getCause();
                if (cause instanceof TimeoutException) {
                    log.warning(
                            "Timeout when shutting down the kafka consumer stream for Connection with ID <{}>",
                            connectionId);
                }
            }
        }
    }

    private void handleStreamCompletion(@Nullable final Done done, @Nullable final Throwable throwable) {
        final ConnectivityStatus status;
        final ResourceStatus statusUpdate;
        final Instant now = Instant.now();
        final Throwable realCause = throwable instanceof CompletionException ? throwable.getCause() : throwable;
        if (null == realCause) {
            status = ConnectivityStatus.CLOSED;
            statusUpdate = ConnectivityModelFactory.newStatusUpdate(
                    InstanceIdentifierSupplier.getInstance().get(),
                    status,
                    sourceAddress,
                    "Consumer closed", now);
        } else if (realCause instanceof MessageRejectedException) {
            final MessageRejectedException cause = (MessageRejectedException) throwable.getCause();
            inboundMonitor.exception(cause);
            status = ConnectivityStatus.CLOSED;
            self().tell(realCause, ActorRef.noSender());
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

    private void restartStream(final MessageRejectedException ex) throws ExecutionException, InterruptedException {
        kafkaStream = kafkaStream.restart().toCompletableFuture().get();
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
