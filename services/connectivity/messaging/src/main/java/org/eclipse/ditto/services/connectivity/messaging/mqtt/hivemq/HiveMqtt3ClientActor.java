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

// TODO: allow setting an own clientId (by the user) instead of using the connection id.

/**
 * Actor which handles connection to MQTT 3.1.1 server.
 */
public final class HiveMqtt3ClientActor extends BaseClientActor {

    // we always want to use clean session -> we need to subscribe after reconnects
    private static final boolean CLEAN_SESSION = true;
    private final HiveMqtt3ClientFactory clientFactory;

    @Nullable private ActorRef publisherActor;

    private final Mqtt3Client client;
    private final HiveMqtt3SubscriptionHandler subscriptionHandler;

    @SuppressWarnings("unused") // used by `props` via reflection
    private HiveMqtt3ClientActor(final Connection connection,
            final ActorRef conciergeForwarder,
            final HiveMqtt3ClientFactory clientFactory) {
        super(connection, connection.getConnectionStatus(), conciergeForwarder);
        this.clientFactory = clientFactory;

        final ActorRef self = getContext().getSelf();
        client = clientFactory.newClient(connection, connection.getId(),
                connected -> self.tell(connected, ActorRef.noSender()),
                disconnected -> self.tell(disconnected, ActorRef.noSender()));

        this.subscriptionHandler = new HiveMqtt3SubscriptionHandler(connection, client,
                failure -> self.tell(failure, ActorRef.noSender()),
                () -> self.tell(getClientReady(), ActorRef.noSender()),
                log);
    }

    @SuppressWarnings("unused")
        // used by `props` via reflection
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


    // TODO: this code is a duplicate of MqttClientActor and KafkaClientActor
    @Override
    protected CompletionStage<Status.Status> doTestConnection(final Connection connection) {
        final Mqtt3Client testClient = clientFactory.newClient(connection, connection.getId());
        return testClient
                .toAsync()
                .connectWith()
                .cleanSession(true)
                .send()
                .thenApply(connAck -> {
                    final String url = connection.getUri();
                    final String message = "MQTT connection to " + url + " established successfully.";
                    log.info(message);
                    return (Status.Status) new Status.Success(message);
                })
                .thenCompose(s -> {
                    log.debug("test connection {} closed after test.", connectionId());
                    return testClient.toAsync().disconnect().thenApply(unused -> s);
                })
                .exceptionally(cause -> {
                    log.info("Connection to {} failed: {}", connection.getUri(), cause.getMessage());
                    return new Status.Failure(cause);
                });

//        // TODO: if we would not try to subscribe to the broker, we could directly map the returned future here.
//        //  does it make sense to start the subscribers? they will subscribe and consume messages (throwing them away though)
//        connectClient(connection, true);
//        return testConnectionFuture;
    }

    @Override
    protected void doConnectClient(final Connection connection, @Nullable final ActorRef origin) {
        connectClient(connection, false, origin);
    }

    @Override
    protected void allocateResourcesOnConnection(final ClientConnected clientConnected) {
        final String connectionId = connection().getId();
        final List<Target> targets = connection().getTargets();

        publisherActor = getContext().actorOf(HiveMqtt3PublisherActor.props(connectionId, targets, client, isDryRun()),
                HiveMqtt3PublisherActor.NAME);
        startMessageMappingProcessorActor(publisherActor);
        startHiveMqConsumers(client, subscriptionHandler::handleMqttConsumer);
    }

    @Override
    protected void doDisconnectClient(final Connection connection, @Nullable final ActorRef origin) {
        final CompletionStage<ClientDisconnected> disconnectFuture = disconnectClient()
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
        stopCommandConsumers();
        stopMessageMappingProcessorActor();
        stopMqttPublisher();
        safelyDisconnectClient();
    }

    /**
     * Call only in case of a failure to make sure the client is closed properly. E.g. when subscribing to a topic
     * failed the connection is already established and must be closed to avoid resource leaks.
     */
    private void safelyDisconnectClient() {
        try {
            disconnectClient();
        } catch (final Throwable throwable) {
            log.debug("Disconnecting client failed, it was probably already closed.");
        }
    }

    private CompletionStage<Void> disconnectClient() {
        return client.toAsync().disconnect();
    }

    private FSM.State<BaseClientState, BaseClientData> handleClientConnected(
            final Mqtt3ClientConnectedContext connected,
            final BaseClientData currentData) {
        log.info("Successfully connected client for connection <{}>.", connectionId());
        subscriptionHandler.handleConnected(connected);
        return stay().using(currentData);
    }

    // callback for the hivemq client
    private FSM.State<BaseClientState, BaseClientData> handleClientDisconnected(
            final Mqtt3ClientDisconnectedContext disconnected,
            final BaseClientData currentData) {

        log.info("Client disconnected <{}>: {}", connectionId(), disconnected.getCause().getMessage());
        subscriptionHandler.handleDisconnected(disconnected);
        return stay().using(currentData);

        // On broker failure while connected:
        //  TODO: reconnect might also be false if settings say it should be false ...
        //  Client for connection <6fbcd2db-9eb0-4a90-a64e-c6d9290488f1> disconnected. Reconnect is set to <true>. Client state is <CONNECTED>. Cause for disconnect: <com.hivemq.client.mqtt.exceptions.ConnectionClosedException: Server closed connection without DISCONNECT.>.

        // On regular Disconnect
        //  Client for connection <6fbcd2db-9eb0-4a90-a64e-c6d9290488f1> disconnected. Reconnect is set to <false>. Client state is <CONNECTED>. Cause for disconnect: <com.hivemq.client.mqtt.mqtt3.exceptions.Mqtt3DisconnectException: Client sent DISCONNECT>.

        // On open port but no MQTT possible:
        //  1. when having status open and then starting the broker:
        //     log.info("Failed on the initial connect.");
        //  2. when opening the connection
        //   Client for connection <6fbcd2db-9eb0-4a90-a64e-c6d9290488f1> disconnected. Reconnect is set to <true>. Client state is <CONNECTING_RECONNECT>. Cause for disconnect: <com.hivemq.client.mqtt.mqtt3.exceptions.Mqtt3DisconnectException: Timeout while waiting for CONNACK>.
        //    shortly later:
        //      Failed on the initial connect


//        if (context.getClientConfig().getState() == CONNECTING) {
//            // we get here if the initial connect fails
//            // see https://github.com/hivemq/hivemq-mqtt-client/issues/302
//            // connectClient() already handles the initial connection failure
//            log.info("Failed on the initial connect.");
//        } else if (context.getClientConfig().getState() == DISCONNECTED) {
//            // TODO: do we get here when trying to disconnect regularly? Do we need to do afterwards?
//            log.info("Disconnected regularly. Doing nothing else??");
//        } else {
//            log.info(
//                    "Client for connection <{}> disconnected. Reconnect is set to <{}>. Client state is <{}>. Cause for disconnect: <{}>.",
//                    connectionId(), context.getReconnector().isReconnect(), context.getClientConfig().getState(),
//                    context.getCause());
//            // TODO: getSelf not ok here since in async callback
//            //  tellToChildren(HiveMqttClientEvents.DISCONNECTED);
//        }
    }

    private void startHiveMqConsumers(final Mqtt3Client client, Consumer<MqttConsumer> consumerListener) {
        final Optional<ActorRef> messageMappingProcessorActor = getMessageMappingProcessorActor();
        if (!messageMappingProcessorActor.isPresent()) {
            log.warning("message mapper not available");
            // TODO goto failure state instead?
            return;
        }

        // TODO: sometimes the hivemq client will run into an exception here if there are multiple consumers on the
        //  same topic. try to do a reproducer and post to hivemq issues / questions
        connection().getSources().stream()
                .map(source -> MqttConsumer.of(source,
                        startHiveMqConsumer(client, isDryRun(), source, messageMappingProcessorActor.get())))
                .forEach(consumerListener);
    }

    private ActorRef startHiveMqConsumer(final Mqtt3Client client, final boolean dryRun, final Source source,
            final ActorRef mappingActor) {
        return startChildActorConflictFree(HiveMqtt3ConsumerActor.NAME,
                HiveMqtt3ConsumerActor.props(connectionId(), mappingActor, source, dryRun));
    }

    /**
     * TODO: there are some problems with connection failures / restarts:
     * When the connection is closed/opened because of some connection failures (broker down, server no mqtt server)
     * and everything goes really fast, the HiveMqtt3PublisherActor can't be restarted. This is because the clientActor
     * still knows the name of the actor and therefore thinks it will start a duplicate.
     *
     * akka's solution for this problem: The clientActor needs to watch the publisher and wait for a Terminated message of
     * it.
     *
     * Another possible solution: Give the actor another name every time an use a wildcard actor selection in the forwarder
     * actor. But don't know yet if this is possible (see comment in the ForwarderActor).
     */

    /**
     * Start MQTT publisher and subscribers, expect "Status.Success" from each of them, then send "ClientConnected" to
     * self.
     *
     * @param connection connection of the publisher and subscribers.
     * @param dryRun if set to true, exchange no message between the broker and the Ditto cluster.
     */
    private void connectClient(final Connection connection, final boolean dryRun, final ActorRef origin) {
        final ActorRef self = getSelf();
        client.toAsync()
                .connectWith()
                .cleanSession(CLEAN_SESSION)
                .send()
                .whenComplete((unused, throwable) -> {
                    if (null != throwable) {
                        // BaseClientActor will handle and log all ConnectionFailures.
                        self.tell(new ImmutableConnectionFailure(null, throwable, null), ActorRef.noSender());
                    } else {
                        // tell self we connected successfully to proceed with connection establishment
                        self.tell(new MqttClientConnected(origin), getSelf());
                    }
                });
    }

    @Override
    protected String getMessageMappingActorName() {
        return MessageMappingProcessorActor.ACTOR_NAME;
    }

    private void stopMqttPublisher() {
        if (publisherActor != null) {
            stopChildActor(publisherActor);
            publisherActor = null;
        }
    }

    private void stopCommandConsumers() {
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
