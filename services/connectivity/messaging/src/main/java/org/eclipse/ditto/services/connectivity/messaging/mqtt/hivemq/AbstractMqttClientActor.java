/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.connectivity.messaging.mqtt.hivemq;

import static org.eclipse.ditto.services.connectivity.messaging.mqtt.hivemq.AbstractMqttSubscriptionHandler.MqttConsumer;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.services.connectivity.messaging.BaseClientActor;
import org.eclipse.ditto.services.connectivity.messaging.BaseClientData;
import org.eclipse.ditto.services.connectivity.messaging.BaseClientState;
import org.eclipse.ditto.services.connectivity.messaging.internal.AbstractWithOrigin;
import org.eclipse.ditto.services.connectivity.messaging.internal.ClientConnected;
import org.eclipse.ditto.services.connectivity.messaging.internal.ClientDisconnected;
import org.eclipse.ditto.services.connectivity.messaging.internal.ImmutableConnectionFailure;
import org.eclipse.ditto.services.connectivity.messaging.mqtt.MqttSpecificConfig;

import akka.actor.ActorRef;
import akka.actor.Status;
import akka.event.DiagnosticLoggingAdapter;
import akka.pattern.Patterns;

/**
 * Actor which handles connection to an MQTT 3 or 5 broker.
 *
 * @param <S> type of Subscribe messages.
 * @param <P> type of Publish messages.
 * @param <Q> type of mQtt client.
 * @param <R> type of subscription Replies.
 */
abstract class AbstractMqttClientActor<S, P, Q, R> extends BaseClientActor {

    // Ask broker to redeliver on reconnect
    // TODO: make this configurable per service together with QoS1 disconnect behavior
    private static final boolean CLEAN_SESSION = false;

    // status for consumer creation (always successful)
    private static final Status.Success CONSUMERS_CREATED = new Status.Success("consumers created");

    private final Connection connection;
    private final HiveMqttClientFactory<Q, ?> clientFactory;

    @Nullable private Q client;
    @Nullable private AbstractMqttSubscriptionHandler<S, P, R> subscriptionHandler;
    @Nullable private ActorRef publisherActor;

    AbstractMqttClientActor(final Connection connection,
            @Nullable final ActorRef conciergeForwarder,
            final ActorRef connectionActor,
            final HiveMqttClientFactory<Q, ?> clientFactory) {

        super(connection, conciergeForwarder, connectionActor);
        this.connection = connection;
        this.clientFactory = clientFactory;
    }

    /**
     * Create a subscription handler.
     *
     * @param connection the connection.
     * @param client the client.
     * @param log the logger of the client actor.
     * @return the subscription handler.
     */
    abstract AbstractMqttSubscriptionHandler<S, P, R> createSubscriptionHandler(Connection connection,
            Q client, DiagnosticLoggingAdapter log);

    /**
     * Send a CONN message.
     *
     * @param client the client to send the message with.
     * @param cleanSession whether to set the clean-session/clean-start flag.
     * @return the CONNACK future.
     */
    abstract CompletionStage<?> sendConn(Q client, boolean cleanSession);

    /**
     * Disconnect the client.
     *
     * @param client the client.
     * @return future that completes when disconnect succeeds.
     */
    abstract CompletionStage<Void> disconnectClient(Q client);

    /**
     * Start a publisher actor for this client actor.
     *
     * @param connection the connection.
     * @param client the client.
     * @return reference of the created publisher actor.
     */
    abstract ActorRef startPublisherActor(Connection connection, Q client);

    /**
     * Start a consumer actor.
     *
     * @param dryRun whether this is a dry-run (consumed messages are discarded)
     * @param source source of the consumer actor.
     * @param mappingActor the message mapping processor actor.
     * @return reference of the created publisher actor.
     */
    abstract ActorRef startConsumerActor(boolean dryRun, Source source, ActorRef mappingActor);

    @Override
    protected void doInit() {
        createClientAndSubscriptionHandler();
    }

    /**
     * Create the client ID for this client actor. Default to connection ID.
     * If duplicate client IDs are problematic, override this method.
     *
     * @param connectionId the connection ID.
     * @param mqttSpecificConfig the specific config.
     * @return the client ID in the config, or use the connection ID as client ID if not configured.
     */
    protected String resolveMqttClientId(final ConnectionId connectionId, final MqttSpecificConfig mqttSpecificConfig) {
        return mqttSpecificConfig.getMqttClientId().orElse(connectionId.toString());
    }

    /**
     * Create a new client using the configuration of this actor.
     * On failure, send a ConnectionFailure to self.
     */
    private void createClientAndSubscriptionHandler() {
        final MqttSpecificConfig mqttSpecificConfig = MqttSpecificConfig.fromConnection(connection);
        final String mqttClientId = resolveMqttClientId(connection.getId(), mqttSpecificConfig);
        final ActorRef self = getContext().getSelf();

        try {
            client = clientFactory.newClient(connection, mqttClientId, true);
            this.subscriptionHandler = createSubscriptionHandler(connection, client, log);
        } catch (final Exception e) {
            log.debug("Connecting failed ({}): {}", e.getClass().getName(), e.getMessage());
            resetClientAndSubscriptionHandler();
            self.tell(new ImmutableConnectionFailure(self, e, null), self);
        }
    }

    private void resetClientAndSubscriptionHandler() {
        client = null;
        subscriptionHandler = null;
    }

    @Override
    protected CompletionStage<Status.Status> doTestConnection(final Connection connection) {

        final MqttSpecificConfig mqttSpecificConfig = MqttSpecificConfig.fromConnection(connection);
        final String mqttClientId = resolveMqttClientId(connection.getId(), mqttSpecificConfig);
        // attention: do not use reconnect, otherwise the future never returns
        final Q testClient;
        try {
            testClient = clientFactory.newClient(connection, mqttClientId, false);
        } catch (final Exception e) {
            return CompletableFuture.completedFuture(new Status.Failure(e.getCause()));
        }
        final AbstractMqttSubscriptionHandler<S, P, R> testSubscriptions =
                createSubscriptionHandler(connection, testClient, log);
        // always use clean session for tests to not have broker persist anything
        final boolean cleanSession = true;
        return sendConn(testClient, cleanSession)
                .thenApply(connAck -> {
                    final String url = connection.getUri();
                    final String message = "MQTT connection to " + url + " established successfully.";
                    log.info("{} {}", message, connAck);
                    return (Status.Status) new Status.Success(message);
                })
                .thenCompose(status -> {
                    startPublisherActor(connection(), testClient);
                    return CompletableFuture.completedFuture(new Status.Success("publisher started"));
                })
                .thenCompose(s -> {
                    startHiveMqConsumers(testSubscriptions::handleMqttConsumer);
                    return testSubscriptions.subscribe();
                })
                .handle((s, t) -> {
                    final Status.Status status;
                    if (t == null) {
                        final String message = "Connection test for was successful.";
                        connectionLogger.success(message);
                        log.info("Connection test for {} was successful.", connectionId());
                        status = new Status.Success(message);
                    } else {
                        log.info("Connection test to {} failed: {}", connection.getUri(), t.getMessage());
                        connectionLogger.failure("Connection test failed: {0}", t.getMessage());
                        status = new Status.Failure(t);
                    }
                    stopCommandConsumers(testSubscriptions);
                    stopChildActor(publisherActor);
                    safelyDisconnectClient(testClient);
                    return status;
                });
    }

    @Override
    protected void doConnectClient(final Connection connection, @Nullable final ActorRef origin) {
        // start publisher and consumer actors first.
        // after that, subscribe and connect in parallel in order to receive redelivered PUBLISH messages.
        if (client == null) {
            createClientAndSubscriptionHandler();
            if (client == null) {
                // client creation failed; a ConnectionFailure event will arrive and cause transition to failure state
                return;
            }
        }
        final CompletionStage<Object> startActorsResult = startPublisherAndConsumerActors(null)
                .thenApply(result -> result.isSuccess() ? new MqttClientConnected(origin) : result.getFailure());
        Patterns.pipe(startActorsResult, getContext().dispatcher()).to(getSelf());
    }

    @Override
    protected State<BaseClientState, BaseClientData> clientConnectedInConnectingState(
            final ClientConnected clientConnected,
            final BaseClientData data) {

        // add subscriber streams right away, but this future won't complete until SUBACK arrive after CONNACK
        final CompletionStage<List<R>> subAckFuture = getSubscriptionHandler().subscribe();

        // delay CONN by 1s to ensure that subscriber streams are ready before redelivered PUBLISH messages arrive
        final CompletableFuture<InitializationResult> connAckFuture = sendConnAndExpectConnAck(Duration.ofSeconds(1L));

        final CompletionStage<InitializationResult> connAckAfterSubAckFuture =
                CompletableFuture.allOf(connAckFuture, subAckFuture.toCompletableFuture())
                        .thenApply(_void -> connAckFuture.join())
                        .exceptionally(InitializationResult::failed);

        Patterns.pipe(connAckAfterSubAckFuture, getContext().getDispatcher())
                .to(getSelf(), clientConnected.getOrigin().orElseGet(ActorRef::noSender));

        return stay();
    }

    private CompletableFuture<InitializationResult> sendConnAndExpectConnAck(final Duration delay) {
        final Q client = getClient();
        final CompletableFuture<Void> delayFuture = new CompletableFuture<>();
        delayFuture.completeOnTimeout(null, delay.toMillis(), TimeUnit.MILLISECONDS);
        return delayFuture.thenCompose(_void ->
                sendConn(client, CLEAN_SESSION).handle((connAck, throwable) -> {
                    if (throwable == null) {
                        log.debug("CONNACK {}", connAck);
                        return InitializationResult.success();
                    } else {
                        log.debug("CONN failed: {}", throwable);
                        return InitializationResult.failed(throwable);
                    }
                })
        );
    }

    @Override
    protected void allocateResourcesOnConnection(final ClientConnected clientConnected) {
        // nothing to do here
    }

    @Override
    protected CompletionStage<Status.Status> startConsumerActors(@Nullable final ClientConnected clientConnected) {
        startHiveMqConsumers(getSubscriptionHandler()::handleMqttConsumer);
        return CompletableFuture.completedFuture(CONSUMERS_CREATED);
    }

    @Override
    protected CompletionStage<Status.Status> startPublisherActor() {
        publisherActor = startPublisherActor(connection(), getClient());
        return CompletableFuture.completedFuture(DONE);
    }

    @Override
    protected void doDisconnectClient(final Connection connection, @Nullable final ActorRef origin) {
        if (client != null) {
            final CompletionStage<ClientDisconnected> disconnectFuture = disconnectClient(getClient())
                    .handle((aVoid, throwable) -> {
                        if (null != throwable) {
                            log.info("Error while disconnecting: {}", throwable);
                        } else {
                            log.debug("Successfully disconnected.");
                        }
                        return getClientDisconnected(origin);
                    });
            Patterns.pipe(disconnectFuture, getContext().getDispatcher()).to(getSelf(), origin);
        } else {
            // client is already disconnected
            getSelf().tell(getClientDisconnected(origin), origin);
        }
    }

    private static ClientDisconnected getClientDisconnected(@Nullable final ActorRef origin) {
        return () -> Optional.ofNullable(origin);
    }

    @Override
    protected void cleanupResourcesForConnection() {
        stopCommandConsumers(subscriptionHandler);
        stopChildActor(publisherActor);
        safelyDisconnectClient(client);
        resetClientAndSubscriptionHandler();
    }

    @Override
    @Nullable
    protected ActorRef getPublisherActor() {
        return publisherActor;
    }

    /**
     * Call only in case of a failure to make sure the client is closed properly. E.g. when subscribing to a topic
     * failed the connection is already established and must be closed to avoid resource leaks.
     *
     * @param client the client to disconnect
     */
    private void safelyDisconnectClient(@Nullable final Q client) {
        if (client != null) {
            try {
                log.debug("Disconnecting mqtt client, ignoring any errors.");
                disconnectClient(client);
            } catch (final Exception e) {
                log.debug("Disconnecting client failed, it was probably already closed.");
            }
        }
    }

    private void startHiveMqConsumers(final Consumer<MqttConsumer> consumerListener) {
        connection().getSources().stream()
                .map(source -> MqttConsumer.of(source,
                        startConsumerActor(isDryRun(), source, getMessageMappingProcessorActor())))
                .forEach(consumerListener);
    }

    private void stopCommandConsumers(@Nullable final AbstractMqttSubscriptionHandler<S, P, R> subscriptionHandler) {
        if (subscriptionHandler != null) {
            subscriptionHandler.clearConsumerActors(this::stopChildActor);
        }
    }

    private Q getClient() {
        if (null == client) {
            throw new IllegalStateException("MqttClient not initialized!");
        }
        return client;
    }

    private AbstractMqttSubscriptionHandler<S, P, R> getSubscriptionHandler() {
        if (null == subscriptionHandler) {
            throw new IllegalStateException("MqttSubscriptionHandler not initialized!");
        }
        return subscriptionHandler;
    }

    static class MqttClientConnected extends AbstractWithOrigin implements ClientConnected {

        MqttClientConnected(@Nullable final ActorRef origin) {
            super(origin);
        }

        @Override
        public Optional<ActorRef> getOrigin() {
            return Optional.empty();
        }
    }

}
