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
package org.eclipse.ditto.connectivity.service.messaging;

import java.time.Duration;
import java.util.Optional;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.api.ConnectivityMessagingConstants;
import org.eclipse.ditto.connectivity.api.commands.sudo.SudoRetrieveClientActorProps;
import org.eclipse.ditto.connectivity.api.commands.sudo.SudoRetrieveConnectionStatus;
import org.eclipse.ditto.connectivity.api.commands.sudo.SudoRetrieveConnectionStatusResponse;
import org.eclipse.ditto.connectivity.model.ConnectivityStatus;
import org.eclipse.ditto.connectivity.model.signals.commands.exceptions.ConnectionNotAccessibleException;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.CloseConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.CloseConnectionResponse;
import org.eclipse.ditto.internal.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.cluster.ShardRegionExtractor;
import org.eclipse.ditto.internal.utils.cluster.ShardedBinaryEnvelope;
import org.eclipse.ditto.internal.utils.cluster.StopShardedActor;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.typesafe.config.Config;

import akka.actor.AbstractActorWithTimers;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.OneForOneStrategy;
import akka.actor.Props;
import akka.actor.SupervisorStrategy;
import akka.actor.Terminated;
import akka.cluster.sharding.ClusterSharding;
import akka.japi.pf.DeciderBuilder;
import akka.japi.pf.ReceiveBuilder;

/**
 * Supervisor of client actors that live in a shard region.
 */
public final class ClientSupervisor extends AbstractActorWithTimers {

    private final DittoDiagnosticLoggingAdapter logger = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    private final ClientActorId clientActorId = ClientActorId.fromActorName(getSelf());
    private final SudoRetrieveConnectionStatus sudoRetrieveConnectionStatus =
            SudoRetrieveConnectionStatus.of(clientActorId.connectionId(),
                    DittoHeaders.newBuilder().correlationId(clientActorId.toString()).build());
    private final Duration statusCheckInterval;
    private final ActorRef connectionShardRegion;
    private final ClientActorPropsFactory propsFactory;
    private Props props;
    private ClientActorPropsArgs clientActorPropsArgs;
    private ActorRef clientActor;

    @SuppressWarnings({"unused"})
    private ClientSupervisor(final int numberOfShards, final Duration statusCheckInterval) {
        this.statusCheckInterval = statusCheckInterval;
        final var actorSystem = getContext().getSystem();
        final var clusterSharding = ClusterSharding.get(actorSystem);
        final var extractor = ShardRegionExtractor.of(numberOfShards, actorSystem);
        connectionShardRegion = clusterSharding.startProxy(ConnectivityMessagingConstants.SHARD_REGION,
                Optional.of(ConnectivityMessagingConstants.CLUSTER_ROLE), extractor);
        propsFactory = getClientActorPropsFactory(actorSystem);
    }

    @SuppressWarnings({"unused"})
    // constructor for unit tests
    private ClientSupervisor(final Duration statusCheckInterval, final ActorRef connectionShardRegion) {
        this.statusCheckInterval = statusCheckInterval;
        this.connectionShardRegion = connectionShardRegion;
        propsFactory = getClientActorPropsFactory(getContext().getSystem());
    }

    /**
     * Create props of this actor.
     *
     * @param numberOfShards the number of shards in connection shard region.
     * @param statusCheckInterval time interval to check the connection status.
     * @return the props object.
     */
    public static Props props(final int numberOfShards, final Duration statusCheckInterval) {
        return Props.create(ClientSupervisor.class, numberOfShards, statusCheckInterval);
    }

    /**
     * Create props for tests.
     *
     * @param statusCheckInterval time interval to check connection status.
     * @param connectionShardRegion the connection shard region.
     * @return the props object.
     */
    public static Props propsForTest(final Duration statusCheckInterval, final ActorRef connectionShardRegion) {
        return Props.create(ClientSupervisor.class, statusCheckInterval, connectionShardRegion);
    }

    @Override
    public void preStart() {
        startStatusCheck(Control.STATUS_CHECK);
    }

    @Override
    public void postStop() {
        logger.debug("Stopped.");
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(ClientActorPropsArgs.class, this::startClientActor)
                .matchEquals(Control.STATUS_CHECK, this::startStatusCheck)
                .match(SudoRetrieveConnectionStatusResponse.class, this::checkConnectionStatus)
                .match(ConnectionNotAccessibleException.class, this::connectionNotAccessible)
                .match(Terminated.class, this::childTerminated)
                .match(CloseConnection.class, this::isNoClientActorStarted, this::respondAndStop)
                .match(ShardedBinaryEnvelope.class, this::extractFromEnvelope)
                .match(StopShardedActor.class, this::restartIfOpen)
                .matchAny(this::forwardToClientActor)
                .build();
    }

    @Override
    public SupervisorStrategy supervisorStrategy() {
        return new OneForOneStrategy(true, DeciderBuilder.matchAny(error -> {
            logger.error(error, "Unexpected error in child, stopping.");
            return SupervisorStrategy.stop();
        }).build());
    }

    private void extractFromEnvelope(final ShardedBinaryEnvelope envelope) {
        getSelf().forward(envelope.message(), getContext());
    }

    private void childTerminated(final Terminated terminated) {
        if (terminated.actor().equals(clientActor)) {
            logger.debug("Stopping self due to child termination");
            getContext().stop(getSelf());
        } else {
            unhandled(terminated);
        }
    }

    private void startClientActor(final ClientActorPropsArgs propsArgs) {
        final var actorProps = propsFactory.getActorProps(propsArgs, getContext().getSystem());
        if (actorProps.equals(this.props)) {
            logger.debug("Refreshing actorProps");
        } else {
            final var oldClientActor = clientActor;
            if (oldClientActor != null) {
                getContext().unwatch(oldClientActor);
                getContext().stop(oldClientActor);
            }
            this.props = actorProps;
            this.clientActorPropsArgs = propsArgs;
            clientActor = getContext().watch(getContext().actorOf(actorProps));
            logger.debug("New actorProps received; stopped client actor <{}> and started <{}>", oldClientActor,
                    clientActor);
        }
    }

    private void forwardToClientActor(final Object message) {
        if (clientActor == null) {
            logger.warning("Unhandled: <{}>", message);
        } else {
            clientActor.forward(message, getContext());
        }
    }

    private void scheduleNextStatusCheck() {
        final long extraMillis = (long) (statusCheckInterval.toMillis() * Math.random());
        final var nextActivityCheck = statusCheckInterval.plusMillis(extraMillis);
        getTimers().startSingleTimer(Control.STATUS_CHECK, Control.STATUS_CHECK, nextActivityCheck);
    }

    private void startStatusCheck(final Control statusCheck) {
        connectionShardRegion.tell(sudoRetrieveConnectionStatus, getSelf());
        scheduleNextStatusCheck();
    }

    private void checkConnectionStatus(final SudoRetrieveConnectionStatusResponse response) {
        if (response.getStatus() == ConnectivityStatus.CLOSED ||
                clientActorId.clientNumber() >= response.getClientCount()) {
            connectionNotAccessible(response);
        } else if (clientActor == null) {
            logger.info("Client actor <{}> of open connection did not start. Requesting props.", clientActorId);
            final var command = SudoRetrieveClientActorProps.of(clientActorId.connectionId(),
                    clientActorId.clientNumber(), DittoHeaders.empty());
            connectionShardRegion.tell(command, getSelf());
        }
    }

    private void connectionNotAccessible(final Object trigger) {
        logger.info("Stopping due to <{}>", trigger);
        getContext().stop(getSelf());
    }

    private boolean isNoClientActorStarted(final Object message) {
        return clientActor == null;
    }

    private void respondAndStop(final CloseConnection command) {
        getSender().tell(CloseConnectionResponse.of(command.getEntityId(), command.getDittoHeaders()), getSelf());
        getContext().stop(getSelf());
    }

    private void restartIfOpen(final StopShardedActor stopShardedActor) {
        if (clientActorPropsArgs != null) {
            logger.debug("Restarting connected client actor.");
            final var envelope = new ShardedBinaryEnvelope(clientActorPropsArgs, clientActorId.toString());
            ClusterSharding.get(getContext().getSystem())
                    .shardRegion(ConnectivityMessagingConstants.CLIENT_SHARD_REGION)
                    .tell(envelope, ActorRef.noSender());
        }
        if (clientActor != null) {
            // wait for ongoing ackregators in the client actor; terminate when the client actor terminates
            clientActor.tell(BaseClientActor.Control.STOP_SHARDED_ACTOR, ActorRef.noSender());
        } else {
            getContext().stop(getSelf());
        }
    }

    private static ClientActorPropsFactory getClientActorPropsFactory(final ActorSystem actorSystem) {
        final Config dittoExtensionConfig = ScopedConfig.dittoExtension(actorSystem.settings().config());
        return ClientActorPropsFactory.get(actorSystem, dittoExtensionConfig);
    }

    private enum Control {
        STATUS_CHECK
    }

}
