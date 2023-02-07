/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.persistence;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;
import javax.jms.JMSRuntimeException;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.service.actors.ShutdownBehaviour;
import org.eclipse.ditto.base.service.config.supervision.ExponentialBackOffConfig;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.signals.commands.ConnectivityCommand;
import org.eclipse.ditto.connectivity.model.signals.commands.exceptions.ConnectionUnavailableException;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.LoggingExpired;
import org.eclipse.ditto.connectivity.service.config.ConnectionConfig;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfigModifiedBehavior;
import org.eclipse.ditto.connectivity.service.config.DittoConnectivityConfig;
import org.eclipse.ditto.connectivity.service.enforcement.ConnectionEnforcerActorPropsFactory;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.streaming.MongoReadJournal;
import org.eclipse.ditto.internal.utils.persistentactors.AbstractPersistenceSupervisor;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorKilledException;
import akka.actor.ActorRef;
import akka.actor.OneForOneStrategy;
import akka.actor.Props;
import akka.actor.ReceiveTimeout;
import akka.actor.SupervisorStrategy;
import akka.japi.pf.DeciderBuilder;
import akka.japi.pf.FI;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.Patterns;

/**
 * Supervisor for {@link ConnectionPersistenceActor} which means it will create, start and watch it as child actor.
 * <p>
 * If the child terminates, it will wait for the calculated exponential back-off time and restart it afterwards.
 * Between the termination of the child and the restart, this actor answers to all requests with a
 * {@link ConnectionUnavailableException} as fail fast strategy.
 * </p>
 */
public final class ConnectionSupervisorActor
        extends AbstractPersistenceSupervisor<ConnectionId, ConnectivityCommand<?>>
        implements ConnectivityConfigModifiedBehavior {

    private static final Duration MAX_CONFIG_RETRIEVAL_DURATION = Duration.ofSeconds(5);

    /**
     * For connectivity, this local ask timeout has to be higher as e.g. "openConnection" commands performed in a
     * "staged" way will lead to quite some response times.
     */
    private static final Duration CONNECTIVITY_DEFAULT_LOCAL_ASK_TIMEOUT = Duration.ofSeconds(50);

    private static final SupervisorStrategy SUPERVISOR_STRATEGY =
            new OneForOneStrategy(true,
                    DeciderBuilder.match(JMSRuntimeException.class, e ->
                                    SupervisorStrategy.resume())
                            .build()
                            .orElse(SupervisorStrategy.stoppingStrategy().decider()));
    private static final Duration OVERWRITES_CHECK_BACKOFF_DURATION = Duration.ofSeconds(30);

    private static final Duration INITIAL_MESSAGE_RECEIVE_TIMEOUT = Duration.ofSeconds(2);

    private final ActorRef commandForwarderActor;
    private final ActorRef pubSubMediator;
    private Config connectivityConfigOverwrites = ConfigFactory.empty();
    private boolean isRegisteredForConnectivityConfigChanges = false;

    private final ConnectionEnforcerActorPropsFactory enforcerActorPropsFactory;

    @SuppressWarnings("unused")
    private ConnectionSupervisorActor(final ActorRef commandForwarderActor,
            final ActorRef pubSubMediator,
            final ConnectionEnforcerActorPropsFactory enforcerActorPropsFactory,
            final MongoReadJournal mongoReadJournal) {

        super(null, mongoReadJournal, CONNECTIVITY_DEFAULT_LOCAL_ASK_TIMEOUT);
        this.commandForwarderActor = commandForwarderActor;
        this.pubSubMediator = pubSubMediator;
        this.enforcerActorPropsFactory = enforcerActorPropsFactory;
    }

    /**
     * Props for creating a {@code ConnectionSupervisorActor}.
     * <p>
     * Exceptions in the child are handled with a supervision strategy that restarts the child on NullPointerExceptions,
     * stops it for {@link ActorKilledException}'s and escalates all others.
     * </p>
     *
     * @param commandForwarder the actor used to send signals into the ditto cluster.
     * @param pubSubMediator pub-sub-mediator for the shutdown behavior.
     * @param enforcerActorPropsFactory used to create the enforcer actor.
     * @param mongoReadJournal the ReadJournal used for gaining access to historical values of the connection.
     * @return the {@link Props} to create this actor.
     */
    public static Props props(final ActorRef commandForwarder,
            final ActorRef pubSubMediator,
            final ConnectionEnforcerActorPropsFactory enforcerActorPropsFactory,
            final MongoReadJournal mongoReadJournal) {

        return Props.create(ConnectionSupervisorActor.class, commandForwarder, pubSubMediator,
                enforcerActorPropsFactory, mongoReadJournal);
    }

    @Override
    protected ConnectionId getEntityId() throws Exception {
        return ConnectionId.of(URLDecoder.decode(getSelf().path().name(), StandardCharsets.UTF_8));
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(Config.class, config -> {
                    this.connectivityConfigOverwrites = config;
                    getSelf().tell(AbstractPersistenceSupervisor.Control.INIT_DONE, getSelf());
                })
                .build()
                .orElse(super.createReceive());
    }

    @Override
    protected Receive activeBehaviour(final Runnable matchProcessNextTwinMessageBehavior,
            final FI.UnitApply<Object> matchAnyBehavior) {
        return ReceiveBuilder.create()
                .match(Config.class, this::onConnectivityConfigModified)
                .match(CheckForOverwritesConfig.class,
                        checkForOverwrites -> initConfigOverwrites(getEntityId(), checkForOverwrites.dittoHeaders))
                .match(RestartConnection.class, restartConnection -> restartConnection.getModifiedConfig()
                        .ifPresentOrElse(this::onConnectivityConfigModified, this::restartChild))
                .matchEquals(Control.REGISTRATION_FOR_CONFIG_CHANGES_SUCCESSFUL, c -> {
                    log.debug("Successfully registered for connectivity config changes.");
                    isRegisteredForConnectivityConfigChanges = true;
                })
                .build()
                .orElse(connectivityConfigModifiedBehavior(getSelf(), () -> persistenceActorChild))
                .orElse(super.activeBehaviour(matchProcessNextTwinMessageBehavior, matchAnyBehavior));
    }

    @Override
    protected boolean shouldBecomeTwinSignalProcessingAwaiting(final Signal<?> signal) {
        return super.shouldBecomeTwinSignalProcessingAwaiting(signal) &&
                !(signal instanceof LoggingExpired) // twin signal without a response
                ;
    }

    @Override
    protected void handleMessagesDuringStartup(final Object message) {
        if (message instanceof WithDittoHeaders withDittoHeaders) {
            initConfigOverwrites(entityId, withDittoHeaders.getDittoHeaders());
        } else if (message.equals(ReceiveTimeout.getInstance())) {
            // this needs to be done to be started as part of "remember-entities" automatic restarting of connections:
            // only after the config overwrites were retrieved, the ConnectionSupervisorActor
            // will move to the "initialized" state and open the connection
            initConfigOverwrites(entityId, null);
        } else if (message != Control.REGISTRATION_FOR_CONFIG_CHANGES_SUCCESSFUL) {
            initConfigOverwrites(entityId, null);
        }
        getContext().cancelReceiveTimeout();
        super.handleMessagesDuringStartup(message);
    }

    @Override
    protected Props getPersistenceActorProps(final ConnectionId entityId) {
        return ConnectionPersistenceActor.props(entityId, mongoReadJournal, commandForwarderActor, pubSubMediator,
                connectivityConfigOverwrites);
    }

    @Override
    protected Props getPersistenceEnforcerProps(final ConnectionId connectionId) {
        return enforcerActorPropsFactory.get(connectionId);
    }

    @Override
    protected ExponentialBackOffConfig getExponentialBackOffConfig() {
        final ConnectionConfig connectionConfig = DittoConnectivityConfig.of(
                DefaultScopedConfig.dittoScoped(getContext().getSystem().settings().config())
        ).getConnectionConfig();
        return connectionConfig.getSupervisorConfig().getExponentialBackOffConfig();
    }

    @Override
    protected ShutdownBehaviour getShutdownBehaviour(final ConnectionId entityId) {
        return ShutdownBehaviour.fromIdWithoutNamespace(entityId, pubSubMediator, getSelf());
    }

    @Override
    protected DittoRuntimeExceptionBuilder<?> getUnavailableExceptionBuilder(@Nullable final ConnectionId entityId) {
        final ConnectionId connectionId = entityId != null ? entityId : ConnectionId.of("UNKNOWN:ID");
        return ConnectionUnavailableException.newBuilder(connectionId);
    }

    @Override
    public SupervisorStrategy supervisorStrategy() {
        return SUPERVISOR_STRATEGY;
    }

    /*
     * This method is called when a config modification is received. Implementations must handle the modified config
     * appropriately i.e. check if any relevant config has changed and re-initialize state if necessary.
     */
    private void onConnectivityConfigModified(final Config modifiedConfig) {
        if (Objects.equals(connectivityConfigOverwrites, modifiedConfig)) {
            log.debug("Received modified config is unchanged, not restarting persistence actor.");
        } else {
            log.info("Restarting persistence actor with modified config: {}", modifiedConfig);
            restartPersistenceActorWithModifiedConfig(modifiedConfig);
        }
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();
        // if no other message is received, the actor was started because of automatic start due to "remember-entities"
        // in that case, we want to call #initConfigOverwrites()
        getContext().setReceiveTimeout(INITIAL_MESSAGE_RECEIVE_TIMEOUT);
    }

    @Override
    protected boolean shouldStartChildImmediately() {
        // do not start persistence actor immediately, wait for retrieval or timeout of connectivity config
        return false;
    }

    private void restartPersistenceActorWithModifiedConfig(final Config connectivityConfigOverwrites) {
        this.connectivityConfigOverwrites = connectivityConfigOverwrites;
        restartChild();
    }

    private void initConfigOverwrites(final ConnectionId connectionId, @Nullable final DittoHeaders dittoHeaders) {
        if (isRegisteredForConnectivityConfigChanges) {
            log.debug("Connection is already initialized with Config: {}", connectionId);
        } else {
            log.debug("Retrieve config overwrites for connection: {}", connectionId);
            Patterns.pipe(retrieveConfigOverwritesOrTimeout(connectionId, dittoHeaders), getContext().getDispatcher())
                    .to(getSelf());
        }
    }

    private CompletionStage<Config> retrieveConfigOverwritesOrTimeout(final ConnectionId connectionId,
            @Nullable final DittoHeaders dittoHeaders) {

        return getConnectivityConfigProvider()
                .getConnectivityConfigOverwrites(connectionId, dittoHeaders)
                .thenApply(config -> {
                    // try to register for connectivity changes after retrieval was successful and the actor is not
                    // yet registered
                    if (!isRegisteredForConnectivityConfigChanges) {
                        getConnectivityConfigProvider()
                                .registerForConnectivityConfigChanges(connectionId, dittoHeaders, getSelf())
                                .thenAccept(unused -> getSelf().tell(Control.REGISTRATION_FOR_CONFIG_CHANGES_SUCCESSFUL,
                                        getSelf()));
                    }
                    return config;
                })
                .toCompletableFuture()
                .orTimeout(MAX_CONFIG_RETRIEVAL_DURATION.toSeconds(), TimeUnit.SECONDS)
                .exceptionally(e -> handleConfigRetrievalException(e, dittoHeaders));
    }

    private void triggerCheckForOverwritesConfig(@Nullable final DittoHeaders dittoHeaders) {
        getTimers().startSingleTimer(CheckForOverwritesConfig.class, new CheckForOverwritesConfig(dittoHeaders),
                OVERWRITES_CHECK_BACKOFF_DURATION);
    }

    private Config handleConfigRetrievalException(final Throwable throwable,
            @Nullable final DittoHeaders dittoHeaders) {
        // If not possible to retrieve overwrites in time, keep going with empty overwrites and potentially restart
        // the actor later.
        log.error(throwable,
                "Could not retrieve overwrites within timeout of <{}>. Persistence actor is started anyway " +
                        "and retrieval of overwrites will be retried after <{}> potentially causing the actor to " +
                        "restart.", MAX_CONFIG_RETRIEVAL_DURATION, OVERWRITES_CHECK_BACKOFF_DURATION);
        triggerCheckForOverwritesConfig(dittoHeaders);
        return ConfigFactory.empty();
    }

    private enum Control {
        REGISTRATION_FOR_CONFIG_CHANGES_SUCCESSFUL
    }

    private static class CheckForOverwritesConfig {

        @Nullable private final DittoHeaders dittoHeaders;

        private CheckForOverwritesConfig(@Nullable final DittoHeaders dittoHeaders) {
            this.dittoHeaders = dittoHeaders;
        }
    }

    /**
     * Command to restart the connection with a modified Config (provided in the command) or unconditional restart if modifiedConfig is null.
     */
    public static final class RestartConnection {

        @Nullable
        private final Config modifiedConfig;

        private RestartConnection(@Nullable final Config modifiedConfig) {
            this.modifiedConfig = modifiedConfig;
        }

        /**
         *
         * @param modifiedConfig a new config to restart connection if changed or {@code null} to restart it unconditionally
         * @return {@link RestartConnection} command class
         */
        public static RestartConnection of(@Nullable final Config modifiedConfig) {
            return new RestartConnection(modifiedConfig);
        }

        /**
         * Getter
         * @return the modified config
         */
        public Optional<Config> getModifiedConfig() {
            return Optional.ofNullable(modifiedConfig);
        }

        @Override
        public boolean equals(@Nullable final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            final RestartConnection that = (RestartConnection) o;
            return Objects.equals(modifiedConfig, that.modifiedConfig);
        }

        @Override
        public int hashCode() {
            return Objects.hash(modifiedConfig);
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " [" +
                    ", modifiedConfig=" + modifiedConfig +
                    "]";
        }
    }

    /**
     * Signals the persistence actor to initiate restart of itself if its type is equal to the specified connectionType.
     */
    public static final class RestartByConnectionType {

        private final ConnectionType connectionType;

        /**
         * Constructor
         * @param connectionType the desired connection type to filter by
         */
        public RestartByConnectionType(final ConnectionType connectionType) {
            this.connectionType = checkNotNull(connectionType, "connectionType");
        }

        /**
         * Getter
         * @return the connection type
         */
        public ConnectionType getConnectionType() {
            return connectionType;
        }
    }
}
