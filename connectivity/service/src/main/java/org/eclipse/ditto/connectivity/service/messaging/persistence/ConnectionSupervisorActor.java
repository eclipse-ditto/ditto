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

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import javax.annotation.Nullable;
import javax.jms.JMSRuntimeException;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.base.service.actors.ShutdownBehaviour;
import org.eclipse.ditto.base.service.config.supervision.ExponentialBackOffConfig;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.signals.commands.ConnectivityCommandInterceptor;
import org.eclipse.ditto.connectivity.model.signals.commands.exceptions.ConnectionUnavailableException;
import org.eclipse.ditto.connectivity.service.config.ConnectionConfig;
import org.eclipse.ditto.connectivity.service.config.DittoConnectivityConfig;
import org.eclipse.ditto.connectivity.service.messaging.ClientActorPropsFactory;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.persistentactors.AbstractPersistenceSupervisor;

import akka.actor.ActorKilledException;
import akka.actor.ActorRef;
import akka.actor.OneForOneStrategy;
import akka.actor.Props;
import akka.actor.SupervisorStrategy;
import akka.japi.pf.DeciderBuilder;

/**
 * Supervisor for {@link ConnectionPersistenceActor} which means it will create, start and watch it as child actor.
 * <p>
 * If the child terminates, it will wait for the calculated exponential back-off time and restart it afterwards.
 * Between the termination of the child and the restart, this actor answers to all requests with a
 * {@link ConnectionUnavailableException} as fail fast strategy.
 * </p>
 */
public final class ConnectionSupervisorActor extends AbstractPersistenceSupervisor<ConnectionId> {

    private static final SupervisorStrategy SUPERVISOR_STRATEGY =
            new OneForOneStrategy(true,
                    DeciderBuilder.match(JMSRuntimeException.class, e ->
                            (SupervisorStrategy.Directive) SupervisorStrategy.resume())
                            .build()
                            .orElse(SupervisorStrategy.stoppingStrategy().decider()));

    private final ActorRef proxyActor;
    private final ClientActorPropsFactory propsFactory;
    @Nullable private final ConnectivityCommandInterceptor commandInterceptor;
    private final ConnectionPriorityProviderFactory connectionPriorityProviderFactory;
    private final ActorRef pubSubMediator;

    @SuppressWarnings("unused")
    private ConnectionSupervisorActor(
            final ActorRef proxyActor,
            final ClientActorPropsFactory propsFactory,
            @Nullable final ConnectivityCommandInterceptor commandInterceptor,
            final ConnectionPriorityProviderFactory connectionPriorityProviderFactory,
            final ActorRef pubSubMediator) {
        this.proxyActor = proxyActor;
        this.propsFactory = propsFactory;
        this.commandInterceptor = commandInterceptor;
        this.connectionPriorityProviderFactory = connectionPriorityProviderFactory;
        this.pubSubMediator = pubSubMediator;
    }

    /**
     * Props for creating a {@code ConnectionSupervisorActor}.
     * <p>
     * Exceptions in the child are handled with a supervision strategy that restarts the child on NullPointerExceptions,
     * stops it for {@link ActorKilledException}'s and escalates all others.
     * </p>
     *
     * @param proxyActor the actor used to send signals into the ditto cluster..
     * @param propsFactory the {@link org.eclipse.ditto.connectivity.service.messaging.ClientActorPropsFactory}
     * @param commandValidator a custom command validator for connectivity commands.
     * @param connectionPriorityProviderFactory used to determine the reconnect priority of a connection.
     * @param pubSubMediator pub-sub-mediator for the shutdown behavior.
     * @return the {@link Props} to create this actor.
     */
    public static Props props(final ActorRef proxyActor,
            final ClientActorPropsFactory propsFactory,
            @Nullable final ConnectivityCommandInterceptor commandValidator,
            final ConnectionPriorityProviderFactory connectionPriorityProviderFactory,
            final ActorRef pubSubMediator) {

        return Props.create(ConnectionSupervisorActor.class, proxyActor, propsFactory, commandValidator,
                connectionPriorityProviderFactory, pubSubMediator);
    }

    @Override
    protected ConnectionId getEntityId() throws Exception {
        return ConnectionId.of(URLDecoder.decode(getSelf().path().name(), StandardCharsets.UTF_8.name()));
    }

    @Override
    protected Props getPersistenceActorProps(final ConnectionId entityId) {
        return ConnectionPersistenceActor.props(entityId, proxyActor, pubSubMediator, propsFactory, commandInterceptor,
                connectionPriorityProviderFactory);
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

}
