/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.connectivity.messaging.kafka;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectivityStatus;
import org.eclipse.ditto.services.connectivity.messaging.BaseClientActor;
import org.eclipse.ditto.services.connectivity.messaging.BaseClientData;
import org.eclipse.ditto.services.connectivity.messaging.BaseClientState;
import org.eclipse.ditto.services.connectivity.messaging.internal.ClientConnected;
import org.eclipse.ditto.services.connectivity.messaging.internal.ClientDisconnected;
import org.eclipse.ditto.services.connectivity.messaging.internal.ConnectionFailure;
import org.eclipse.ditto.services.connectivity.messaging.internal.ImmutableConnectionFailure;
import org.eclipse.ditto.services.connectivity.util.ConnectionConfigReader;
import org.eclipse.ditto.services.connectivity.util.KafkaConfigReader;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Status;
import akka.japi.pf.FSMStateFunctionBuilder;

/**
 * TODO: implement fully
 * TODO: unit test
 * Actor which handles connection to Kafka server.
 */
public final class KafkaClientActor extends BaseClientActor {

    private ActorRef kafkaPublisherActor;

    private final Set<ActorRef> pendingStatusReportsFromStreams;
    // TODO: is this created only once per connection? why would we need this bifunction then? we could create the
    //  connectionFactory on startup.
    private final BiFunction<Connection, DittoHeaders, KafkaConnectionFactory> connectionFactoryCreator;

    private CompletableFuture<Status.Status> testConnectionFuture = null;

    @SuppressWarnings("unused") // used by `props` via reflection
    private KafkaClientActor(final Connection connection,
            final ConnectivityStatus desiredConnectionStatus,
            final ActorRef conciergeForwarder) {
        super(connection, desiredConnectionStatus, conciergeForwarder);
        final KafkaConfigReader configReader = ConnectionConfigReader.fromRawConfig(getContext().system().settings().config()).kafka();
        this.connectionFactoryCreator = (c, headers) -> KafkaConnectionFactory.of(c, headers, configReader);
        pendingStatusReportsFromStreams = new HashSet<>();
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @param connection the connection.
     * @param conciergeForwarder the actor used to send signals to the concierge service.
     * @return the Akka configuration Props object.
     */
    public static Props props(final Connection connection, final ActorRef conciergeForwarder) {
        return Props.create(KafkaClientActor.class, validateConnection(connection), connection.getConnectionStatus(),
                conciergeForwarder);
    }

    private static Connection validateConnection(final Connection connection) {
        // TODO: think about it
        // nothing to do so far
        return connection;
    }

    @Override
    protected FSMStateFunctionBuilder<BaseClientState, BaseClientData> inTestingState() {
        return super.inTestingState()
                .event(Status.Status.class, (e, d) -> !Objects.equals(getSender(), getSelf()),
                        this::handleStatusReportFromChildren)
                .event(ClientConnected.class, BaseClientData.class, (event, data) -> {
                    final String url = data.getConnection().getUri();
                    final String message = "Kafka connection to " + url + " established successfully";
                    completeTestConnectionFuture(new Status.Success(message));
                    return stay();
                })
                .event(ConnectionFailure.class, BaseClientData.class, (event, data) -> {
                    completeTestConnectionFuture(new Status.Failure(event.getFailure().cause()));
                    return stay();
                });
    }

    @Override
    protected FSMStateFunctionBuilder<BaseClientState, BaseClientData> inConnectingState() {
        return super.inConnectingState()
                .event(Status.Status.class, this::handleStatusReportFromChildren);
    }

    @Override
    protected CompletionStage<Status.Status> doTestConnection(final Connection connection) {
        if (testConnectionFuture != null) {
            final Exception error = new IllegalStateException("Can't test new connection since a test is already running.");
            return CompletableFuture.completedFuture(new Status.Failure(error));
        }
        testConnectionFuture = new CompletableFuture<>();
        connectClient(connection, true);
        return testConnectionFuture;
    }

    @Override
    protected void allocateResourcesOnConnection(final ClientConnected clientConnected) {
        // nothing to do here; publisher and consumers started already.
    }

    @Override
    protected Optional<ActorRef> getPublisherActor() {
        return Optional.ofNullable(kafkaPublisherActor);
    }

    @Override
    protected void doConnectClient(final Connection connection, @Nullable final ActorRef origin) {
        connectClient(connection, false);
    }

    @Override
    protected void doDisconnectClient(final Connection connection, @Nullable final ActorRef origin) {
        self().tell((ClientDisconnected) () -> null, origin);
    }

    /**
     * Start Kafka publishers, expect "Status.Success" from each of them, then send "ClientConnected" to
     * self.
     *
     * @param connection connection of the publishers.
     * @param dryRun if set to true, exchange no message between the broker and the Ditto cluster.
     */
    private void connectClient(final Connection connection, final boolean dryRun) {
        final KafkaConnectionFactory factory =
                connectionFactoryCreator.apply(connection, stateData().getSessionHeaders());

        // start publisher
        startKafkaPublisher(factory, dryRun);
    }

    private void startKafkaPublisher(final KafkaConnectionFactory factory, final boolean dryRun) {
        log.info("Starting Kafka publisher actor.");
        // ensure no previous publisher stays in memory
        stopKafkaPublisher();
        kafkaPublisherActor = startChildActorConflictFree(KafkaPublisherActor.ACTOR_NAME,
                KafkaPublisherActor.props(connectionId(), getTargetsOrEmptyList(), factory, getSelf(), dryRun));
        pendingStatusReportsFromStreams.add(kafkaPublisherActor);
    }

    @Override
    protected void cleanupResourcesForConnection() {

        pendingStatusReportsFromStreams.clear();
        stopKafkaPublisher();
    }


    private void stopKafkaPublisher() {
        if (kafkaPublisherActor != null) {
            // TODO: since we started the actor inside kafkaPublisherActor using Sink.newActorRef,
            //  we need to send a Status.Success or Status.Failure to it first. it will then push all remaining messages
            //  i think and then stop the actor. Otherwise the Source will keep consuming even if the actor was killed.
            //  We will also need to fix this for MQTT and maybe the others, too.
            log.debug("Stopping child actor <{}>.", kafkaPublisherActor.path());
            // shutdown using a message, so the actor can clean up first
            kafkaPublisherActor.tell(KafkaPublisherActor.GracefulStop.INSTANCE, getSelf());
            kafkaPublisherActor = null;
        }
    }

    private State<BaseClientState, BaseClientData> handleStatusReportFromChildren(final Status.Status status,
            final BaseClientData data) {
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

    private void completeTestConnectionFuture(final Status.Status testResult) {
        if (testConnectionFuture != null) {
            testConnectionFuture.complete(testResult);
        } else {
            // no future; test failed.
            final Exception exception = new IllegalStateException("Could not complete testing connection since the test was already completed or wasn't started.");
            getSelf().tell(new Status.Failure(exception), getSelf());
        }
    }
}
