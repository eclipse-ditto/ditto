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

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.services.connectivity.messaging.BaseClientActor;
import org.eclipse.ditto.services.connectivity.messaging.BaseClientData;
import org.eclipse.ditto.services.connectivity.messaging.BaseClientState;
import org.eclipse.ditto.services.connectivity.messaging.MessageMappingProcessorActor;
import org.eclipse.ditto.services.connectivity.messaging.internal.AbstractWithOrigin;
import org.eclipse.ditto.services.connectivity.messaging.internal.ClientConnected;
import org.eclipse.ditto.services.connectivity.messaging.internal.ClientDisconnected;
import org.eclipse.ditto.services.connectivity.messaging.internal.ImmutableConnectionFailure;
import org.eclipse.ditto.services.connectivity.messaging.mqtt.MqttSpecificConfig;
import org.eclipse.ditto.services.connectivity.messaging.mqtt.hivemq.HiveMqtt3SubscriptionHandler.MqttConsumer;

import com.hivemq.client.mqtt.mqtt3.Mqtt3Client;
import com.hivemq.client.mqtt.mqtt3.lifecycle.Mqtt3ClientConnectedContext;
import com.hivemq.client.mqtt.mqtt3.lifecycle.Mqtt3ClientDisconnectedContext;

import akka.actor.ActorRef;
import akka.actor.FSM;
import akka.actor.Props;
import akka.actor.Status;
import akka.japi.pf.FSMStateFunctionBuilder;
import akka.pattern.Patterns;

/**
 * Actor which handles connection to MQTT 3.1.1 server.
 */
public final class HiveMqtt3ClientActor extends BaseClientActor {

    // we always want to use clean session -> we need to subscribe after reconnects
    private static final boolean CLEAN_SESSION = true;
    private final HiveMqtt3ClientFactory clientFactory;

    private final Mqtt3Client client;
    private final HiveMqtt3SubscriptionHandler subscriptionHandler;

    @SuppressWarnings("unused") // used by `props` via reflection
    private HiveMqtt3ClientActor(final Connection connection,
            final ActorRef conciergeForwarder,
            final HiveMqtt3ClientFactory clientFactory) {
        super(connection, connection.getConnectionStatus(), conciergeForwarder);
        this.clientFactory = clientFactory;

        final MqttSpecificConfig mqttSpecificConfig = MqttSpecificConfig.fromConnection(connection);
        final String mqttClientId = mqttSpecificConfig.getMqttClientId().orElse(connection.getId());

        final ActorRef self = getContext().getSelf();
        client = clientFactory.newClient(connection, mqttClientId, true,
                connected -> self.tell(connected, ActorRef.noSender()),
                disconnected -> self.tell(disconnected, ActorRef.noSender()));

        this.subscriptionHandler = new HiveMqtt3SubscriptionHandler(connection, client,
                failure -> {
                    connectionLogger.failure("Failed to subscribe: {0}", failure.getFailureDescription());
                    self.tell(failure, ActorRef.noSender());
                },
                () -> {
                    connectionLogger.success("Successfully initialized subscriptions.");
                    notifyConsumersReady();
                }, log);
    }

    @SuppressWarnings("unused") // used by `props` via reflection
    HiveMqtt3ClientActor(final Connection connection,
            final ActorRef conciergeForwarder) {
        this(connection, conciergeForwarder, DefaultHiveMqtt3ClientFactory.getInstance());
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @param connection the connection.
     * @param conciergeForwarder the actor used to send signals to the concierge service.
     * @param clientFactory factory used to create required mqtt clients
     * @return the Akka configuration Props object.
     */
    public static Props props(final Connection connection, final ActorRef conciergeForwarder,
            final HiveMqtt3ClientFactory clientFactory) {
        return Props.create(HiveMqtt3ClientActor.class, validateConnection(connection),
                conciergeForwarder, clientFactory);
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @param connection the connection.
     * @param conciergeForwarder the actor used to send signals to the concierge service.
     * @return the Akka configuration Props object.
     */
    public static Props props(final Connection connection, final ActorRef conciergeForwarder) {
        return Props.create(HiveMqtt3ClientActor.class, validateConnection(connection),
                conciergeForwarder);
    }

    @Override
    protected FSMStateFunctionBuilder<BaseClientState, BaseClientData> inAnyState() {
        return super.inAnyState()
                .event(Mqtt3ClientConnectedContext.class, this::handleClientConnected)
                .event(Mqtt3ClientDisconnectedContext.class, this::handleClientDisconnected);
    }

    private static Connection validateConnection(final Connection connection) {
        // nothing to do so far
        return connection;
    }

    @Override
    protected CompletionStage<Status.Status> doTestConnection(final Connection connection) {

        final MqttSpecificConfig mqttSpecificConfig = MqttSpecificConfig.fromConnection(connection);
        final String mqttClientId = mqttSpecificConfig.getMqttClientId().orElse(connection.getId());
        // attention: do not use reconnect, otherwise the future never returns
        final Mqtt3Client testClient = clientFactory.newClient(connection, mqttClientId, false);
        final CompletableFuture<Status.Status> subscriptionsFuture = new CompletableFuture<>();
        final HiveMqtt3SubscriptionHandler testSubscriptions = new HiveMqtt3SubscriptionHandler(connection, testClient,
                f -> subscriptionsFuture.completeExceptionally(f.getFailure().cause()),
                () -> subscriptionsFuture.complete(new Status.Success("subscriptions started")), log);
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
                    startPublisherActor(connectionId(), connection().getTargets(), testClient);
                    return CompletableFuture.completedFuture(new Status.Success("publisher started"));
                })
                .thenCompose(s -> {
                    testSubscriptions.handleConnected();
                    startHiveMqConsumers(testSubscriptions::handleMqttConsumer);
                    return subscriptionsFuture;
                })
                .exceptionally(cause -> {
                    log.info("Connection test to {} failed: {}", connection.getUri(), cause.getMessage());
                    connectionLogger.failure("Connection test failed: {0}", cause.getMessage());
                    return new Status.Failure(cause);
                })
                .whenComplete((s, t) -> {
                    if (t == null) {
                        connectionLogger.success("Connection test for was successful.");
                        log.info("Connection test for {} was successful.", connectionId());
                    }
                    stopCommandConsumers(testSubscriptions);
                    stopPublisherActor();
                    safelyDisconnectClient(testClient);
                });
    }

    @Override
    protected void doConnectClient(final Connection connection, @Nullable final ActorRef origin) {
        final ActorRef self = getSelf();
        client.toAsync()
                .connectWith()
                .cleanSession(CLEAN_SESSION)
                .send()
                .whenComplete((unused, throwable) -> {
                    if (null != throwable) {
                        // BaseClientActor will handle and log all ConnectionFailures.
                        log.debug("Connecting failed ({}): {}", throwable.getClass().getName(), throwable.getMessage());
                        self.tell(new ImmutableConnectionFailure(origin, throwable, null), origin);
                    } else {
                        // tell self we connected successfully to proceed with connection establishment
                        connectionLogger.success("Connection to {0} established successfully.",
                                connection.getHostname());
                        self.tell(new MqttClientConnected(origin), getSelf());
                    }
                });
    }

    @Override
    protected void allocateResourcesOnConnection(final ClientConnected clientConnected) {
        final String connectionId = connection().getId();
        final List<Target> targets = connection().getTargets();
        startPublisherActor(connectionId, targets, client);
        startHiveMqConsumers(subscriptionHandler::handleMqttConsumer);
    }

    private void startPublisherActor(final String connectionId, final List<Target> targets, final Mqtt3Client client) {
        final Props publisherActorProps = HiveMqtt3PublisherActor.props(connectionId, targets, client, isDryRun());
        publisherActor = getContext().actorOf(publisherActorProps, HiveMqtt3PublisherActor.NAME);
    }

    @Override
    protected void doDisconnectClient(final Connection connection, @Nullable final ActorRef origin) {
        final CompletionStage<ClientDisconnected> disconnectFuture = disconnectClient(client)
                .handle((aVoid, throwable) -> {
                    if (null != throwable) {
                        log.info("Error while disconnecting: {}", throwable);
                    } else {
                        log.debug("Successfully disconnected.");
                    }
                    return getClientDisconnected(origin);
                });
        Patterns.pipe(disconnectFuture, getContext().getDispatcher()).to(getSelf(), origin);
    }

    private static ClientDisconnected getClientDisconnected(@Nullable final ActorRef origin) {
        return () -> Optional.ofNullable(origin);
    }

    @Override
    protected void cleanupResourcesForConnection() {
        stopCommandConsumers(subscriptionHandler);
        stopPublisherActor();
        safelyDisconnectClient(client);
    }

    /**
     * Call only in case of a failure to make sure the client is closed properly. E.g. when subscribing to a topic
     * failed the connection is already established and must be closed to avoid resource leaks.
     * @param mqtt3Client the client to disconnect
     */
    private void safelyDisconnectClient(final Mqtt3Client mqtt3Client) {
        try {
            log.debug("Disconnecting mqtt client, ignoring any errors.");
            disconnectClient(mqtt3Client);
        } catch (final Throwable throwable) {
            log.debug("Disconnecting client failed, it was probably already closed.");
        }
    }

    private CompletionStage<Void> disconnectClient(final Mqtt3Client mqtt3Client) {
        return mqtt3Client.toAsync().disconnect();
    }

    private FSM.State<BaseClientState, BaseClientData> handleClientConnected(
            final Mqtt3ClientConnectedContext connected,
            final BaseClientData currentData) {
        log.debug("Successfully connected client for connection <{}>.", connectionId());
        connectionLogger.success("Client connected.");
        subscriptionHandler.handleConnected();
        return stay().using(currentData);
    }

    private FSM.State<BaseClientState, BaseClientData> handleClientDisconnected(
            final Mqtt3ClientDisconnectedContext disconnected,
            final BaseClientData currentData) {
        log.debug("Client disconnected <{}>: {}", connectionId(), disconnected.getCause().getMessage());
        connectionLogger.failure("Client disconnected: {0}", disconnected.getCause().getMessage());
        subscriptionHandler.handleDisconnected();
        return stay().using(currentData);
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

    @Override
    protected String getMessageMappingActorName() {
        return MessageMappingProcessorActor.ACTOR_NAME;
    }

    private void stopCommandConsumers(final HiveMqtt3SubscriptionHandler subscriptionHandler) {
        subscriptionHandler.clearConsumerActors(this::stopChildActor);
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
