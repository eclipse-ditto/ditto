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
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.clients.GenericMqttClient;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.clients.GenericMqttConnAckStatus;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.clients.GenericMqttConnect;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.clients.HiveMqttClientProperties;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.consuming.MqttConsumerActor;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.publishing.GenericMqttPublishingClient;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.publishing.MqttPublisherActor;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.subscribing.AllSubscriptionsFailedException;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.subscribing.GenericMqttSubscribingClient;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.subscribing.MqttSubscribeException;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.subscribing.MqttSubscriber;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.subscribing.SomeSubscriptionsFailedException;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.subscribing.SourceSubscribeResult;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.subscribing.SubscriptionStatus;
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

    private final Function<HiveMqttClientProperties, GenericMqttSubscribingClient> subscribingClientFactory;
    private final Function<HiveMqttClientProperties, GenericMqttPublishingClient> publishingClientFactory;
    private final HiveMqttClientProperties hiveMqttClientProperties;
    private final Sink<Object, NotUsed> inboundMappingSink;
    private final ConnectivityStatusResolver connectivityStatusResolver;
    private final ChildActorNanny childActorNanny;
    private final ClassicActorSystemProvider systemProvider;
    private final ThreadSafeDittoLogger logger;

    private ConnectionTester(final Builder builder) {
        subscribingClientFactory = checkNotNull(builder.subscribingClientFactory, "subscribingClientFactory");
        publishingClientFactory = checkNotNull(builder.publishingClientFactory, "publishingClientFactory");
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
        return tryToGetSubscribingClient()
                .thenCombine(tryToGetPublishingClient(), (subscribingClient, publishingClient) ->
                        CompletableFuture.allOf(connectClient(subscribingClient), connectClient(publishingClient))
                                .thenApply(logMqttConnectionEstablished())
                                .thenApply(startPublisherActor(publishingClient))
                                .thenCombine(subscribe(subscribingClient), handleTotalSubscribeResult())
                                .handle(getAppropriateStatus(publishingClient, subscribingClient))
                )
                .thenCompose(Function.identity())
                .exceptionally(this::logFailureAndGetStatus);
    }

    private CompletionStage<GenericMqttSubscribingClient> tryToGetSubscribingClient() {
        try {
            return CompletableFuture.completedFuture(subscribingClientFactory.apply(hiveMqttClientProperties));
        } catch (final Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private CompletionStage<GenericMqttPublishingClient> tryToGetPublishingClient() {
        try {
            return CompletableFuture.completedFuture(publishingClientFactory.apply(hiveMqttClientProperties));
        } catch (final Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private static CompletableFuture<GenericMqttConnAckStatus> connectClient(final GenericMqttClient client) {
        return client.connect(GenericMqttConnect.newInstance(true, KeepAliveInterval.zero())).toCompletableFuture();
    }

    private Function<Void, Void> logMqttConnectionEstablished() {
        final var mqttConnection = hiveMqttClientProperties.getMqttConnection();
        logger.info("Established MQTT connection to <{}>.", mqttConnection.getUri());
        return Function.identity();
    }

    private Function<Void, ActorRef> startPublisherActor(final GenericMqttPublishingClient publishingClient) {
        return aVoid -> childActorNanny.startChildActorConflictFree(
                MqttPublisherActor.class.getSimpleName(),
                MqttPublisherActor.propsDryRun(hiveMqttClientProperties.getMqttConnection(),
                        connectivityStatusResolver,
                        hiveMqttClientProperties.getConnectivityConfig(),
                        publishingClient)
        );
    }

    private CompletionStage<TotalSubscribeResult> subscribe(final GenericMqttSubscribingClient subscribingClient) {
        final var mqttConnection = hiveMqttClientProperties.getMqttConnection();
        return MqttSubscriber.subscribeForConnectionSources(mqttConnection.getSources(), subscribingClient)
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
                        totalSubscribeResult.successfulSourceSubscribeResults().map(this::startConsumerActor),
                        Stream.of(producerActorRef)
                );
            }
        };
    }

    private static MqttSubscribeException newMqttSubscribeExceptionForFailedSourceSubscribeResults(
            final TotalSubscribeResult totalSubscribeResult
    ) {
        throw new MqttSubscribeException(
                totalSubscribeResult.failedSourceSubscribeResults()
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

    private ActorRef startConsumerActor(final SourceSubscribeResult subscribeSuccess) {
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
            final GenericMqttClient publishingClient,
            final GenericMqttClient subscribingClient
    ) {
        return (childActorRefs, error) -> {
            final Status.Status result;
            if (null == error) {
                result = handleConnectionTestSuccess(childActorRefs);
            } else {
                result = logFailureAndGetStatus(error);
            }
            publishingClient.disconnect();
            subscribingClient.disconnect();
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

        private Function<HiveMqttClientProperties, GenericMqttSubscribingClient> subscribingClientFactory;
        private Function<HiveMqttClientProperties, GenericMqttPublishingClient> publishingClientFactory;
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

        Builder withSubscribingClientFactory(
                final Function<HiveMqttClientProperties, GenericMqttSubscribingClient> subscribingClientFactory
        ) {
            this.subscribingClientFactory = checkNotNull(subscribingClientFactory, "subscribingClientFactory");
            return this;
        }

        Builder withPublishingClientFactory(
                final Function<HiveMqttClientProperties, GenericMqttPublishingClient> publishingClientFactory
        ) {
            this.publishingClientFactory = checkNotNull(publishingClientFactory, "publishingClientFactory");
            return this;
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

}
