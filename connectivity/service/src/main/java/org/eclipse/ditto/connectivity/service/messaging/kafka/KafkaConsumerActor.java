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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.service.config.supervision.ExponentialBackOffConfig;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.ConnectivityStatus;
import org.eclipse.ditto.connectivity.model.ResourceStatus;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.config.KafkaConsumerConfig;
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
    private static final Duration MAX_SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);
    private static final int DEFAULT_CONSUMPTION_QOS = 0;

    private final ThreadSafeDittoLoggingAdapter log;
    private RestartableKafkaConsumerStream kafkaStream;

    @SuppressWarnings("unused")
    private KafkaConsumerActor(final Connection connection,
            final KafkaConsumerStreamFactory streamFactory,
            final ConsumerData consumerData,
            final Sink<Object, NotUsed> inboundMappingSink,
            final ConnectivityStatusResolver connectivityStatusResolver,
            final ConnectivityConfig connectivityConfig) {
        super(connection, consumerData.getAddress(), inboundMappingSink, consumerData.getSource(),
                connectivityStatusResolver, connectivityConfig);

        final KafkaConsumerConfig consumerConfig = connectivityConfig
                .getConnectionConfig()
                .getKafkaConfig()
                .getConsumerConfig();
        final ExponentialBackOffConfig exponentialBackOffConfig = consumerConfig.getRestartBackOffConfig();
        log = DittoLoggerFactory.getThreadSafeDittoLoggingAdapter(this);
        final Materializer materializer = Materializer.createMaterializer(this::getContext);
        final Integer qos = source.getQos().orElse(DEFAULT_CONSUMPTION_QOS);
        if (qos.equals(1)) {
            kafkaStream = new RestartableKafkaConsumerStream(
                    () -> {
                        final KafkaConsumerStream kafkaConsumerStream =
                                streamFactory.newAtLeastOnceConsumerStream(materializer, inboundMonitor,
                                        inboundAcknowledgedMonitor, getMessageMappingSink(),
                                        getDittoRuntimeExceptionSink(),
                                        connection.getId(),
                                        consumerData.getActorNamePrefix());
                        kafkaConsumerStream.whenComplete(this::handleStreamCompletion);
                        return kafkaConsumerStream;
                    }, exponentialBackOffConfig);
        } else {
            kafkaStream = new RestartableKafkaConsumerStream(
                    () -> {
                        final KafkaConsumerStream kafkaConsumerStream =
                                streamFactory.newAtMostOnceConsumerStream(materializer, inboundMonitor,
                                        getMessageMappingSink(), getDittoRuntimeExceptionSink(), connection.getId(),
                                        consumerData.getActorNamePrefix());
                        kafkaConsumerStream.whenComplete(this::handleStreamCompletion);
                        return kafkaConsumerStream;
                    }, exponentialBackOffConfig);
        }
        timers().startTimerAtFixedRate(ReportMetrics.class, ReportMetrics.INSTANCE,
                consumerConfig.getMetricCollectingInterval());
    }

    static Props props(final Connection connection,
            final KafkaConsumerStreamFactory streamFactory,
            final ConsumerData consumerData,
            final Sink<Object, NotUsed> inboundMappingSink,
            final ConnectivityStatusResolver connectivityStatusResolver,
            final ConnectivityConfig connectivityConfig) {
        return Props.create(KafkaConsumerActor.class, connection, streamFactory, consumerData,
                inboundMappingSink, connectivityStatusResolver, connectivityConfig);
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
                .match(GracefulStop.class, stop -> shutdown())
                .match(ReportMetrics.class, reportMetrics -> reportMetrics())
                .match(MessageRejectedException.class, this::restartStream)
                .match(RestartableKafkaConsumerStream.class, this::setStream)
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

    private void reportMetrics() {
        kafkaStream.reportMetrics();
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
        getContext().stop(self());
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
            status = ConnectivityStatus.CLOSED;
            self().tell(realCause, ActorRef.noSender());
            statusUpdate = ConnectivityModelFactory.newStatusUpdate(
                    InstanceIdentifierSupplier.getInstance().get(),
                    status,
                    sourceAddress,
                    "Restarting because of rejected message.", now);
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

    private void restartStream(final MessageRejectedException ex) {
        kafkaStream.restart().toCompletableFuture()
                .thenAccept(newKafkaStream -> self().tell(newKafkaStream, self()));
    }

    private void setStream(final RestartableKafkaConsumerStream newKafkaStream) {
        kafkaStream = newKafkaStream;
        resetResourceStatus();
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

    static final class ReportMetrics {

        static final ReportMetrics INSTANCE = new ReportMetrics();

        private ReportMetrics() {
            // intentionally empty
        }
    }

}
