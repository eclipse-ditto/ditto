/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.connectivity.service.messaging.ChildActorNanny;
import org.eclipse.ditto.connectivity.service.messaging.ConnectivityStatusResolver;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.KeepAliveInterval;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client.AllSubscriptionsFailedException;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client.GenericMqttClient;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client.GenericMqttClientFactory;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client.HiveMqttClientProperties;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client.MqttSubscribeException;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client.SomeSubscriptionsFailedException;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client.SubscriptionStatus;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.consuming.MqttConsumerActor;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.connect.GenericMqttConnect;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.publishing.MqttPublisherActor;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.subscribing.MqttSubscriber;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.subscribing.SubscribeResult;
import org.eclipse.ditto.connectivity.service.util.ConnectivityMdcEntryKey;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLogger;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ClassicActorSystemProvider;
import akka.actor.Status;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;

/**
 * Tests whether an MQTT {@link org.eclipse.ditto.connectivity.model.Connection} can successfully be established
 * by connecting to the MQTT broker.
 */
@NotThreadSafe
final class ConnectionTester {

    private final HiveMqttClientProperties hiveMqttClientProperties;
    private final Sink<Object, NotUsed> inboundMappingSink;
    private final ConnectivityStatusResolver connectivityStatusResolver;
    private final ChildActorNanny childActorNanny;
    private final ClassicActorSystemProvider systemProvider;
    private final ThreadSafeDittoLogger logger;

    private ConnectionTester(final Builder builder) {
        hiveMqttClientProperties = checkNotNull(builder.hiveMqttClientProperties, "hiveMqttClientProperties");
        inboundMappingSink = checkNotNull(builder.inboundMappingSink, "inboundMappingSink");
        connectivityStatusResolver = checkNotNull(builder.connectivityStatusResolver, "connectivityStatusResolver");
        childActorNanny = checkNotNull(builder.childActorNanny, "childActorNanny");
        systemProvider = checkNotNull(builder.systemProvider, "systemProvider");

        final var mqttConnection = hiveMqttClientProperties.getMqttConnection();
        logger = DittoLoggerFactory.getThreadSafeLogger(getClass())
                .withCorrelationId(builder.correlationId)
                .withMdcEntry(ConnectivityMdcEntryKey.CONNECTION_ID, mqttConnection.getId());
    }

    /**
     * Returns a mutable builder with a fluent API for constructing a {@code ConnectionTester} object.
     *
     * @return the builder.
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Performs the MQTT connection test.
     */
    CompletionStage<Status.Status> testConnection() {
        return tryToGetGenericMqttClient()
                .thenCompose(genericMqttClient ->
                        connectClient(genericMqttClient)
                                .thenApply(logMqttConnectionEstablished())
                                .thenApply(startPublisherActor())
                                .thenCombine(subscribe(genericMqttClient), handleTotalSubscribeResult())
                                .handle(getAppropriateStatus(genericMqttClient))
                )
                .exceptionally(this::logFailureAndGetStatus);
    }

    private CompletionStage<GenericMqttClient> tryToGetGenericMqttClient() {
        try {
            return CompletableFuture.completedFuture(
                    GenericMqttClientFactory.getGenericMqttClientForConnectionTesting(hiveMqttClientProperties)
            );
        } catch (final Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private static CompletableFuture<GenericMqttClient> connectClient(final GenericMqttClient client) {
        return client.connect(GenericMqttConnect.newInstance(true, KeepAliveInterval.zero()))
                .thenApply(unusedVoid -> client)
                .toCompletableFuture();
    }

    private Function<GenericMqttClient, GenericMqttClient> logMqttConnectionEstablished() {
        final var mqttConnection = hiveMqttClientProperties.getMqttConnection();
        logger.info("Established MQTT connection to <{}>.", mqttConnection.getUri());
        return Function.identity();
    }

    private Function<GenericMqttClient, ActorRef> startPublisherActor() {
        return genericMqttClient -> childActorNanny.startChildActorConflictFree(
                MqttPublisherActor.class.getSimpleName(),
                MqttPublisherActor.propsDryRun(hiveMqttClientProperties.getMqttConnection(),
                        connectivityStatusResolver,
                        hiveMqttClientProperties.getConnectivityConfig(),
                        genericMqttClient)
        );
    }

    private CompletionStage<TotalSubscribeResult> subscribe(final GenericMqttClient genericMqttClient) {
        final var mqttConnection = hiveMqttClientProperties.getMqttConnection();
        final var mqttSubscriber = MqttSubscriber.newInstance(genericMqttClient);
        return mqttSubscriber.subscribeForConnectionSources(mqttConnection.getSources())
                .toMat(Sink.seq(), Keep.right())
                .run(systemProvider)
                .thenApply(TotalSubscribeResult::of);
    }

    private BiFunction<ActorRef, TotalSubscribeResult, Stream<ActorRef>> handleTotalSubscribeResult() {
        return (producerActorRef, totalSubscribeResult) -> {
            if (totalSubscribeResult.hasFailures()) {
                childActorNanny.stopChildActor(producerActorRef);
                throw newMqttSubscribeExceptionForFailedSourceSubscribeResults(totalSubscribeResult);
            } else {
                return Stream.concat(
                        totalSubscribeResult.successfulSubscribeResults().map(this::startConsumerActor),
                        Stream.of(producerActorRef)
                );
            }
        };
    }

    private static MqttSubscribeException newMqttSubscribeExceptionForFailedSourceSubscribeResults(
            final TotalSubscribeResult totalSubscribeResult
    ) {
        throw new MqttSubscribeException(
                totalSubscribeResult.failedSubscribeResults()
                        .flatMap(sourceSubscribeResult -> {
                            final Stream<String> result;
                            final var mqttSubscribeException = sourceSubscribeResult.getErrorOrThrow();
                            if (mqttSubscribeException instanceof AllSubscriptionsFailedException allFailed) {
                                final var failedSubscriptionStatuses = allFailed.getFailedSubscriptionStatuses();
                                result = failedSubscriptionStatuses.stream().map(SubscriptionStatus::toString);
                            } else if (mqttSubscribeException instanceof SomeSubscriptionsFailedException someFailed) {
                                final var failedSubscriptionStatuses = someFailed.getFailedSubscriptionStatuses();
                                result = failedSubscriptionStatuses.stream().map(SubscriptionStatus::toString);
                            } else {
                                final var connectionSource = sourceSubscribeResult.getConnectionSource();
                                result = Stream.of(MessageFormat.format("{0}: {1}",
                                        mqttSubscribeException.getMessage(),
                                        connectionSource.getAddresses()));
                            }
                            return result;
                        })
                        .collect(Collectors.joining(", ", "[", "]")),
                null
        );
    }

    private ActorRef startConsumerActor(final SubscribeResult subscribeSuccess) {
        return childActorNanny.startChildActorConflictFree(
                MqttConsumerActor.class.getSimpleName(),
                MqttConsumerActor.propsDryRun(hiveMqttClientProperties.getMqttConnection(),
                        inboundMappingSink,
                        subscribeSuccess.getConnectionSource(),
                        connectivityStatusResolver,
                        hiveMqttClientProperties.getConnectivityConfig(),
                        subscribeSuccess.getMqttPublishSourceOrThrow())
        );
    }

    private BiFunction<Stream<ActorRef>, Throwable, Status.Status> getAppropriateStatus(
            final GenericMqttClient genericMqttClient
    ) {
        return (childActorRefs, error) -> {
            final Status.Status result;
            if (null == error) {
                result = handleConnectionTestSuccess(childActorRefs);
            } else {
                result = logFailureAndGetStatus(error);
            }
            genericMqttClient.disconnect();
            return result;
        };
    }

    private Status.Status handleConnectionTestSuccess(final Stream<ActorRef> childActorRefs) {
        final var mqttConnection = hiveMqttClientProperties.getMqttConnection();
        logger.info("Test for connection <{}> at <{}> was successful.",
                mqttConnection.getId(),
                mqttConnection.getUri());

        final var successMessage = "Connection test was successful.";
        final var connectionLogger = hiveMqttClientProperties.getConnectionLogger();
        connectionLogger.success(successMessage);

        childActorRefs.forEach(childActorNanny::stopChildActor);

        return new Status.Success(successMessage);
    }

    private Status.Status logFailureAndGetStatus(final Throwable completionStageException) {
        final var error = unwrapCauseIfCompletionException(completionStageException);

        final var mqttConnection = hiveMqttClientProperties.getMqttConnection();
        logger.info("Test for connection <{}> at <{}> failed: {}",
                mqttConnection.getId(),
                mqttConnection.getUri(),
                error.getMessage());

        final var connectionLogger = hiveMqttClientProperties.getConnectionLogger();
        connectionLogger.failure("Connection test failed: {0}", error.getMessage());

        return new Status.Failure(error);
    }

    private static Throwable unwrapCauseIfCompletionException(final Throwable throwable) {
        final Throwable result;
        if (throwable instanceof CompletionException e) {
            result = e.getCause();
        } else {
            result = throwable;
        }
        return result;
    }

    /**
     * Mutable builder with a fluent API for constructing a {@code ConnectionTester} object.
     * The {@link #build()} method throws a NullPointerException if any mandatory property was not set.
     */
    @NotThreadSafe
    static final class Builder {

        private HiveMqttClientProperties hiveMqttClientProperties;
        private Sink<Object, NotUsed> inboundMappingSink;
        private ConnectivityStatusResolver connectivityStatusResolver;
        private ChildActorNanny childActorNanny;
        private ClassicActorSystemProvider systemProvider;
        @Nullable private CharSequence correlationId;

        private Builder() {
            hiveMqttClientProperties = null;
            inboundMappingSink = null;
            connectivityStatusResolver = null;
            childActorNanny = null;
            systemProvider = null;
            correlationId = null;
        }

        Builder withHiveMqttClientProperties(final HiveMqttClientProperties hiveMqttClientProperties) {
            this.hiveMqttClientProperties = checkNotNull(hiveMqttClientProperties, "hiveMqttClientProperties");
            return this;
        }

        Builder withInboundMappingSink(final Sink<Object, NotUsed> inboundMappingSink) {
            this.inboundMappingSink = checkNotNull(inboundMappingSink, "inboundMappingSink");
            return this;
        }

        Builder withConnectivityStatusResolver(final ConnectivityStatusResolver connectivityStatusResolver) {
            this.connectivityStatusResolver = checkNotNull(connectivityStatusResolver, "connectivityStatusResolver");
            return this;
        }

        Builder withChildActorNanny(final ChildActorNanny childActorNanny) {
            this.childActorNanny = checkNotNull(childActorNanny, "childActorNanny");
            return this;
        }

        Builder withActorSystemProvider(final ClassicActorSystemProvider actorSystemProvider) {
            systemProvider = checkNotNull(actorSystemProvider, "actorSystemProvider");
            return this;
        }

        Builder withCorrelationId(@Nullable final CharSequence correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        ConnectionTester build() {
            return new ConnectionTester(this);
        }

    }

    /**
     * Bundles a finite amount of {@link SubscribeResult}s for determining failures.
     */
    static final class TotalSubscribeResult {

        private final List<SubscribeResult> successfulSubscribeResults;
        private final List<SubscribeResult> failedSubscribeResults;

        private TotalSubscribeResult(final List<SubscribeResult> successfulSubscribeResults,
                final List<SubscribeResult> failedSubscribeResults) {

            this.successfulSubscribeResults = List.copyOf(successfulSubscribeResults);
            this.failedSubscribeResults = List.copyOf(failedSubscribeResults);
        }

        /**
         * Returns an instance of {@code TotalSubscribeResult} for the specified list argument.
         *
         * @param sourceSubscribeResults a list of {@code SubscribeResult}s which are regarded as a whole to represent
         * a total subscribe result.
         * @return the instance.
         * @throws NullPointerException if {@code sourceSubscribeResults} is {@code null}.
         */
         static TotalSubscribeResult of(final List<SubscribeResult> sourceSubscribeResults) {
            checkNotNull(sourceSubscribeResults, "sourceSubscribeResults");
            final var subscribeResultsByIsSuccess =
                    sourceSubscribeResults.stream().collect(Collectors.partitioningBy(SubscribeResult::isSuccess));
            return new TotalSubscribeResult(subscribeResultsByIsSuccess.get(true),
                    subscribeResultsByIsSuccess.get(false));
        }

        boolean hasFailures() {
            return !failedSubscribeResults.isEmpty();
        }

        Stream<SubscribeResult> successfulSubscribeResults() {
            return successfulSubscribeResults.stream();
        }

        Stream<SubscribeResult> failedSubscribeResults() {
            return failedSubscribeResults.stream();
        }

        @Override
        public boolean equals(@Nullable final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final var that = (TotalSubscribeResult) o;
            return Objects.equals(successfulSubscribeResults, that.successfulSubscribeResults) &&
                    Objects.equals(failedSubscribeResults, that.failedSubscribeResults);
        }

        @Override
        public int hashCode() {
            return Objects.hash(successfulSubscribeResults, failedSubscribeResults);
        }

    }

}
