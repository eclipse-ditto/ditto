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
import org.eclipse.ditto.services.connectivity.messaging.mqtt.hivemq.HiveMqtt3SubscriptionHandler.MqttConsumer;

import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client;
import com.hivemq.client.mqtt.mqtt3.message.subscribe.suback.Mqtt3SubAck;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Status;
import akka.pattern.Patterns;

/**
 * Actor which handles connection to MQTT 3.1.1 server.
 */
public final class HiveMqtt3ClientActor extends BaseClientActor {

    // Ask broker to redeliver on reconnect
    private static final boolean CLEAN_SESSION = false;

    // status for consumer creation (always successful)
    private static final Status.Success CONSUMERS_CREATED = new Status.Success("consumers created");

    private final Connection connection;
    private final HiveMqtt3ClientFactory clientFactory;

    @Nullable private Mqtt3AsyncClient client;
    @Nullable private HiveMqtt3SubscriptionHandler subscriptionHandler;
    @Nullable private ActorRef publisherActor;

    @SuppressWarnings("unused") // used by `props` via reflection
    private HiveMqtt3ClientActor(final Connection connection,
            @Nullable final ActorRef conciergeForwarder,
            final ActorRef connectionActor,
            final HiveMqtt3ClientFactory clientFactory) {

        super(connection, conciergeForwarder, connectionActor);
        this.connection = connection;
        this.clientFactory = clientFactory;
    }

    @SuppressWarnings("unused") // used by `props` via reflection
    private HiveMqtt3ClientActor(final Connection connection, @Nullable final ActorRef conciergeForwarder,
            final ActorRef connectionActor) {
        this(connection, conciergeForwarder, connectionActor, DefaultHiveMqtt3ClientFactory.getInstance());
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @param connection the connection.
     * @param conciergeForwarder the actor used to send signals to the concierge service.
     * @param connectionActor the connectionPersistenceActor which created this client.
     * @param clientFactory factory used to create required mqtt clients
     * @return the Akka configuration Props object.
     */
    public static Props props(final Connection connection, @Nullable final ActorRef conciergeForwarder,
            final ActorRef connectionActor, final HiveMqtt3ClientFactory clientFactory) {
        return Props.create(HiveMqtt3ClientActor.class, validateConnection(connection), conciergeForwarder,
                connectionActor, clientFactory);
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @param connection the connection.
     * @param conciergeForwarder the actor used to send signals to the concierge service.
     * @param connectionActor the connectionPersistenceActor which created this client.
     * @return the Akka configuration Props object.
     */
    public static Props props(final Connection connection, @Nullable final ActorRef conciergeForwarder,
            final ActorRef connectionActor) {
        return Props.create(HiveMqtt3ClientActor.class, validateConnection(connection), conciergeForwarder,
                connectionActor);
    }

    private Mqtt3AsyncClient getClient() {
        if (null == client) {
            throw new IllegalStateException("Mqtt3Client not initialized!");
        }
        return client;
    }

    private HiveMqtt3SubscriptionHandler getSubscriptionHandler() {
        if (null == subscriptionHandler) {
            throw new IllegalStateException("HiveMqtt3SubscriptionHandler not initialized!");
        }
        return subscriptionHandler;
    }

    @Override
    protected void doInit() {
        createClientAndSubscriptionHandler();
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
            client = clientFactory.newClient(connection, mqttClientId, true).toAsync();
            this.subscriptionHandler = new HiveMqtt3SubscriptionHandler(connection, client, log);
            if (log.isDebugEnabled()) {
                client.publishes(MqttGlobalPublishFilter.UNSOLICITED,
                        publish -> log.debug("UNSOLICITED {}", publish));
            }
        } catch (final Exception e) {
            log.debug("Connecting failed ({}): {}", e.getClass().getName(), e.getMessage());
            self.tell(new ImmutableConnectionFailure(self, e, null), self);
        }
    }

    private void resetClientAndSubscriptionHandler() {
        client = null;
        subscriptionHandler = null;
    }

    private String resolveMqttClientId(final ConnectionId connectionId, final MqttSpecificConfig mqttSpecificConfig) {
        return mqttSpecificConfig.getMqttClientId().orElse(connectionId.toString());
    }

    private static Connection validateConnection(final Connection connection) {
        // nothing to do so far
        return connection;
    }

    @Override
    protected CompletionStage<Status.Status> doTestConnection(final Connection connection) {

        final MqttSpecificConfig mqttSpecificConfig = MqttSpecificConfig.fromConnection(connection);
        final String mqttClientId = resolveMqttClientId(connection.getId(), mqttSpecificConfig);
        // attention: do not use reconnect, otherwise the future never returns
        final Mqtt3AsyncClient testClient;
        try {
            testClient = clientFactory.newClient(connection, mqttClientId, false).toAsync();
        } catch (final Exception e) {
            return CompletableFuture.completedFuture(new Status.Failure(e.getCause()));
        }
        final HiveMqtt3SubscriptionHandler testSubscriptions =
                new HiveMqtt3SubscriptionHandler(connection, testClient, log);
        return testClient
                .toAsync()
                .connectWith()
                .cleanSession(CLEAN_SESSION)
                .send()
                .thenApply(connAck -> {
                    final String url = connection.getUri();
                    final String message = "MQTT connection to " + url + " established successfully.";
                    log.info(message);
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
        final CompletionStage<List<Mqtt3SubAck>> subAckFuture = getSubscriptionHandler().subscribe();

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
        final Mqtt3AsyncClient client = getClient();
        final CompletableFuture<Void> delayFuture = new CompletableFuture<>();
        delayFuture.completeOnTimeout(null, delay.toMillis(), TimeUnit.MILLISECONDS);
        return delayFuture.thenCompose(_void -> client.connectWith()
                .cleanSession(CLEAN_SESSION)
                .send()
                .handle((connAck, throwable) -> {
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

    private ActorRef startPublisherActor(final Connection connection, final Mqtt3Client client) {
        final Props publisherActorProps = HiveMqtt3PublisherActor.props(connection, client, isDryRun());
        return startChildActorConflictFree(HiveMqtt3PublisherActor.NAME, publisherActorProps);
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
     * @param mqtt3Client the client to disconnect
     */
    private void safelyDisconnectClient(@Nullable final Mqtt3Client mqtt3Client) {
        if (mqtt3Client != null) {
            try {
                log.debug("Disconnecting mqtt client, ignoring any errors.");
                disconnectClient(mqtt3Client);
            } catch (final Exception e) {
                log.debug("Disconnecting client failed, it was probably already closed.");
            }
        }
    }

    private CompletionStage<Void> disconnectClient(final Mqtt3Client mqtt3Client) {
        return mqtt3Client.toAsync().disconnect();
    }

    private void startHiveMqConsumers(final Consumer<MqttConsumer> consumerListener) {
        connection().getSources().stream()
                .map(source -> MqttConsumer.of(source,
                        startHiveMqConsumer(isDryRun(), source, getMessageMappingProcessorActor())))
                .forEach(consumerListener);
    }

    private ActorRef startHiveMqConsumer(final boolean dryRun, final Source source, final ActorRef mappingActor) {
        return startChildActorConflictFree(HiveMqtt3ConsumerActor.NAME,
                HiveMqtt3ConsumerActor.props(connectionId(), mappingActor, source, dryRun));
    }

    private void stopCommandConsumers(@Nullable final HiveMqtt3SubscriptionHandler subscriptionHandler) {
        if (subscriptionHandler != null) {
            subscriptionHandler.clearConsumerActors(this::stopChildActor);
        }
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
