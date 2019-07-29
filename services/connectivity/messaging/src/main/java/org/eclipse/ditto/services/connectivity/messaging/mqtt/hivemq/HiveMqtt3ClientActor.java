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

import static com.hivemq.client.mqtt.MqttClientState.*;
import static com.hivemq.client.mqtt.MqttClientState.CONNECTING;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectivityStatus;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.services.connectivity.messaging.BaseClientActor;
import org.eclipse.ditto.services.connectivity.messaging.BaseClientData;
import org.eclipse.ditto.services.connectivity.messaging.BaseClientState;
import org.eclipse.ditto.services.connectivity.messaging.MessageMappingProcessorActor;
import org.eclipse.ditto.services.connectivity.messaging.internal.ClientConnected;
import org.eclipse.ditto.services.connectivity.messaging.internal.ClientDisconnected;
import org.eclipse.ditto.services.connectivity.messaging.internal.ConnectionFailure;
import org.eclipse.ditto.services.connectivity.messaging.internal.ImmutableConnectionFailure;
import org.eclipse.ditto.utils.jsr305.annotations.AllParametersAndReturnValuesAreNonnullByDefault;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.lifecycle.MqttClientConnectedContext;
import com.hivemq.client.mqtt.lifecycle.MqttClientDisconnectedContext;
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client;
import com.hivemq.client.mqtt.mqtt3.Mqtt3ClientBuilder;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Status;
import akka.actor.Terminated;
import akka.japi.pf.FSMStateFunctionBuilder;

// TODO: allow setting an own clientId (by the user) instead of using the connection id.
/**
 * TODO: test
 * Actor which handles connection to MQTT 3.1.1 server.
 */
@AllParametersAndReturnValuesAreNonnullByDefault
public final class HiveMqtt3ClientActor extends BaseClientActor {

    private static final boolean CLEAN_SESSION = true;

    @Nullable
    private ActorRef publisherActor;
    private final ActorRef forwarderToPublisherActor;
    private final List<ActorRef> consumerActors;

    private final ActorRef forwarderToMessageMappingActor;

    private final Mqtt3Client client;
    private final Set<ActorRef> pendingStatusReportsFromStreams;

    private CompletableFuture<Status.Status> testConnectionFuture = null;

    @SuppressWarnings("unused") // used by `props` via reflection
    HiveMqtt3ClientActor(final Connection connection,
            final ConnectivityStatus desiredConnectionStatus,
            final ActorRef conciergeForwarder) {

        super(connection, desiredConnectionStatus, conciergeForwarder);
        pendingStatusReportsFromStreams = new HashSet<>();

        client = buildHiveMqClient(connection);
        forwarderToPublisherActor = startForwarder(HiveMqtt3PublisherActor.NAME);
        forwarderToMessageMappingActor = startForwarder(MessageMappingProcessorActor.ACTOR_NAME);
        consumerActors = new ArrayList<>();
    }

    private ActorRef startForwarder(final String actorName) {
        return getContext().actorOf(ForwardActorWithStrategy.forwardTo(getContext().actorSelection(
                actorName)), "forwarder-to-" + actorName);
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
                connection.getConnectionStatus(),
                conciergeForwarder);
    }

    private static Connection validateConnection(final Connection connection) {
        // nothing to do so far
        return connection;
    }

    @Override
    protected FSMStateFunctionBuilder<BaseClientState, BaseClientData> inTestingState() {
        return super.inTestingState()
                .event(Status.Status.class, (e, d) -> !Objects.equals(getSender(), getSelf()),
                        this::handleStatusReportFromChildren)
                .event(ClientConnected.class, BaseClientData.class, (event, data) -> {
                    // TODO: this code is a duplicate of MqttClientActor and KafkaClientActor
                    final String url = data.getConnection().getUri();
                    final String message = "MQTT connection to " + url + " established successfully.";
                    completeTestConnectionFuture(new Status.Success(message), data);
                    return stay();
                })
                .event(ConnectionFailure.class, BaseClientData.class, (event, data) -> {
                    completeTestConnectionFuture(new Status.Failure(event.getFailure().cause()), data);
                    return stay();
                });
    }

    @Override
    protected FSMStateFunctionBuilder<BaseClientState, BaseClientData> inConnectingState() {
        return super.inConnectingState()
                .event(Status.Status.class, this::handleStatusReportFromChildren);
    }

    // TODO: this code is a duplicate of MqttClientActor and KafkaClientActor
    @Override
    protected CompletionStage<Status.Status> doTestConnection(final Connection connection) {
        if (testConnectionFuture != null) {
            final Exception error = new IllegalStateException("test future exists");
            return CompletableFuture.completedFuture(new Status.Failure(error));
        }
        testConnectionFuture = new CompletableFuture<>();
        // TODO: if we would not try to subscribe to the broker, we could directly map the returned future here.
        //  does it make sense to start the subscribers? they will subscribe and consume messages (throwing them away though)
        connectClient(connection, true);
        return testConnectionFuture;
    }

    @Override
    protected void allocateResourcesOnConnection(final ClientConnected clientConnected) {
        // nothing to do here; publisher and consumers started already.
    }

    @Override
    protected void doConnectClient(final Connection connection, @Nullable final ActorRef origin) {
        connectClient(connection, false);
    }

    @Override
    protected void doDisconnectClient(final Connection connection, @Nullable final ActorRef origin) {
        final ActorRef self = self();
        disconnectClient().whenComplete((aVoid, throwable) -> {
            if (null != throwable) {
                log.warning("Error while disconnecting: {}", throwable);
            } else {
                log.info("Successfully disconnected.");
            }
            self.tell((ClientDisconnected) () -> null, origin);
        });
    }

    private CompletionStage<Void> disconnectClient() {
        return client.toAsync().disconnect();
    }

    // callback for the hivemq client
    private void onClientConnected(final MqttClientConnectedContext context) {
        log.info("Successfully connected client for connection <{}>.", connectionId());
        final HiveMqttClientEvents event;
        switch (context.getClientConfig().getState()) {
            case CONNECTING_RECONNECT:
                event = HiveMqttClientEvents.RECONNECTED;
                break;
            case CONNECTING:
            default:
                event = HiveMqttClientEvents.CONNECTED;
        }
        tellToChildren(event);
    }

    // callback for the hivemq client
    private void onClientDisconnected(final MqttClientDisconnectedContext context) {
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


        if (context.getClientConfig().getState() == CONNECTING) {
            // we get here if the initial connect fails
            // see https://github.com/hivemq/hivemq-mqtt-client/issues/302
            // connectClient() already handles the initial connection failure
            log.info("Failed on the initial connect.");
        } else if (context.getClientConfig().getState() == DISCONNECTED) {
            // TODO: do we get here when trying to disconnect regularly? Do we need to do afterwards?
            log.info("Disconnected regularly. Doing nothing else??");
        } else {
            log.info(
                    "Client for connection <{}> disconnected. Reconnect is set to <{}>. Client state is <{}>. Cause for disconnect: <{}>.",
                    connectionId(), context.getReconnector().isReconnect(), context.getClientConfig().getState(),
                    context.getCause());
            // TODO: getSelf not ok here since in async callback
            tellToChildren(HiveMqttClientEvents.DISCONNECTED);
        }

    }

    private Mqtt3Client buildHiveMqClient(final Connection connection) {
        Mqtt3ClientBuilder mqtt3ClientBuilder = MqttClient.builder().useMqttVersion3();
        final Optional<String> possibleUsername = connection.getUsername();
        final Optional<String> possiblePassword = connection.getPassword();
        if (possibleUsername.isPresent() && possiblePassword.isPresent()) {
            mqtt3ClientBuilder = mqtt3ClientBuilder.simpleAuth()
                    .username(possibleUsername.get())
                    .password(possiblePassword.get().getBytes(StandardCharsets.UTF_8))
                    .applySimpleAuth();
        }

        if (connection.isFailoverEnabled()) {
            // TODO: use specific config instead of default config
            mqtt3ClientBuilder.automaticReconnectWithDefaultConfig();
        }

        if ("ssl".equals(connection.getProtocol()) || "wss".equals(connection.getProtocol())) {
            // TODO: apply TLS config
        }

        return mqtt3ClientBuilder.identifier(connection.getId())
                .addConnectedListener(this::onClientConnected)
                .addDisconnectedListener(this::onClientDisconnected)
                .build();
    }

    private List<ActorRef> startHiveMqConsumers(final Mqtt3Client client, final boolean dryRun) {
        if (isConsuming()) {
            // TODO: try out if consumer count will work or if we'll need diferent names for all consumers
            return connection().getSources().stream()
                    .map(source -> startHiveMqConsumer(client, dryRun, source))
                    .flatMap(Collection::stream)
                    .collect(Collectors.toList());
        }

        log.info("Not starting consumption because there is no source.");
        return Collections.emptyList();
    }

    private List<ActorRef> startHiveMqConsumer(final Mqtt3Client client, final boolean dryRun, final Source source) {
        if (source.getConsumerCount() <= 0) {
            log.info("source #{} has {} consumer - not starting consumer actor", source.getIndex(), source.getConsumerCount());
            return Collections.emptyList();
        }
        return Stream.iterate(0, i -> i + 1)
                .limit(source.getConsumerCount())
                .map(unused -> getContext().actorOf(HiveMqtt3ConsumerActor.props(
                        // TODO: just use the source as parameter instead of accessing attributes of it...
                        connectionId(),
                        forwarderToMessageMappingActor,
                        source.getAuthorizationContext(),
                        source.getEnforcement().orElse(null),
                        dryRun,
                        source.getAddresses(),
                        client
                )))
                .collect(Collectors.toList());
    }

    @Override
    protected FSMStateFunctionBuilder<BaseClientState, BaseClientData> inAnyState() {
        return super.inAnyState()
                .event(Terminated.class, BaseClientData.class, (terminated, data) -> {
                    log.error("Terminated {}", terminated);
                    return stay();
                });
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
    private void connectClient(final Connection connection, final boolean dryRun) {
        // TODO: do we really want to start the actors before connecting succeeded?
        publisherActor =
                getContext().actorOf(HiveMqtt3PublisherActor.props(connection.getId(), connection.getTargets(),
                        client,
                        dryRun), HiveMqtt3PublisherActor.NAME);
        pendingStatusReportsFromStreams.add(publisherActor);
        consumerActors.addAll(startHiveMqConsumers(client, dryRun));
        pendingStatusReportsFromStreams.addAll(consumerActors);

        startMessageMappingProcessorActor(forwarderToPublisherActor);

        final ActorRef self = getSelf();
        client.toAsync()
                .connectWith()
                .cleanSession(CLEAN_SESSION)
                .send()
                .whenComplete((unused, throwable) -> {
                    if (null != throwable) {
                        // BaseClientActor will handle and log all ConnectionFailures.
                        self.tell(new ImmutableConnectionFailure(null, throwable, null), ActorRef.noSender());
                    }
                }); // will automatically start subscriptions and publishers in the onconnected and ondisconnected callbacks of the client
    }

    @Override
    protected String getMessageMappingActorName() {
        return MessageMappingProcessorActor.ACTOR_NAME;
    }

    @Override
    protected void cleanupResourcesForConnection() {
        pendingStatusReportsFromStreams.clear();
        stopCommandConsumers();
        stopMessageMappingProcessorActor();
        stopMqttPublisher();

        // TODO: this is already called in "doDisconnectClient" - this will probably cause more problems then it will do good.
        //  here the calls of this method:
        //  1. socket is closed
        //  2. StateTimeout during CONNECTING or DISONNECTING - here there would also be called cleanupFurtherResourcesOnConnectionTimeout
        //  3. on a ClientDisconnected message
        //  4. on a ConnectionFailure message
        disconnectClient();
    }


    private void stopMqttPublisher() {
        if (publisherActor != null) {
            stopChildActor(publisherActor);
            publisherActor = null;
        }
    }

    private void stopCommandConsumers() {
        consumerActors.forEach(this::stopChildActor);
        consumerActors.clear();
    }

    // TODO: duplicated code in MqttClientActor and KafkaClientActor
    private State<BaseClientState, BaseClientData> handleStatusReportFromChildren(final Status.Status status,
            final BaseClientData data) {

        log.info("Status from {} with status {}. Still pending {}", getSender(), status,
                pendingStatusReportsFromStreams);
        if (pendingStatusReportsFromStreams.contains(getSender())) {
            pendingStatusReportsFromStreams.remove(getSender());
            if (status instanceof Status.Failure) {
                final Status.Failure failure = (Status.Failure) status;
                final ConnectionFailure connectionFailure =
                        new ImmutableConnectionFailure(null, failure.cause(), "child failed");
                getSelf().tell(connectionFailure, ActorRef.noSender());
            } else if (pendingStatusReportsFromStreams.isEmpty()) {
                // all children are ready; this client actor is connected.
                getSelf().tell((ClientConnected) () -> null, ActorRef.noSender());
            }
        }
        return stay();
    }

    // TODO: duplicated code in MqttClientActor and KafkaClientActor
    private void completeTestConnectionFuture(final Status.Status testResult, final BaseClientData data) {
        if (testConnectionFuture != null) {
            testConnectionFuture.complete(testResult);
        } else {
            // no future; test failed.
            final Exception exception = new IllegalStateException("test future not found");
            getSelf().tell(new Status.Failure(exception), getSelf());
        }
    }

    private void tellToChildren(final Object message) {
        log.info("Telling all my child consumers and publishers: {}", message);
        consumerActors.forEach(actor -> actor.tell(message, getSelf()));
        if (publisherActor != null) {
            publisherActor.tell(message, getSelf());
        }
    }

    enum HiveMqttClientEvents {
        CONNECTED,
        RECONNECTED,
        DISCONNECTED
    }

}
