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
package org.eclipse.ditto.connectivity.service.messaging.validation;

import java.util.Optional;
import java.util.function.Supplier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.signals.commands.ConnectivityCommand;
import org.eclipse.ditto.connectivity.model.signals.commands.ConnectivityCommandInterceptor;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.CreateConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.ModifyConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.OpenConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.TestConnection;
import org.eclipse.ditto.connectivity.service.messaging.ClientActorPropsFactory;

import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;

/**
 * Checks if the given {@link ConnectivityCommand} is valid by trying to create the client actor props.
 */
public final class DittoConnectivityCommandValidator implements ConnectivityCommandInterceptor {

    private final ClientActorPropsFactory propsFactory;
    private final ActorRef proxyActor;
    private final ActorRef connectionActor;
    private final ConnectionValidator connectionValidator;
    private final ActorSystem actorSystem;

    public DittoConnectivityCommandValidator(
            final ClientActorPropsFactory propsFactory,
            final ActorRef proxyActor,
            final ActorRef connectionActor,
            final ConnectionValidator connectionValidator,
            final ActorSystem actorSystem) {

        this.propsFactory = propsFactory;
        this.proxyActor = proxyActor;
        this.connectionActor = connectionActor;
        this.connectionValidator = connectionValidator;
        this.actorSystem = actorSystem;
    }

    @Override
    public void accept(final ConnectivityCommand<?> command, final Supplier<Connection> connectionSupplier) {
        switch (command.getType()) {
            case CreateConnection.TYPE, TestConnection.TYPE, ModifyConnection.TYPE ->
                    resolveConnection(connectionSupplier)
                            .ifPresentOrElse(connection -> {
                                        connectionValidator.validate(connection, command.getDittoHeaders(), actorSystem);
                                        propsFactory.getActorPropsForType(connection, proxyActor, connectionActor, actorSystem,
                                                command.getDittoHeaders(), ConfigFactory.empty());
                                    },
                                    // should never happen
                                    handleNullConnection(command));
            case OpenConnection.TYPE ->
                    resolveConnection(connectionSupplier).ifPresentOrElse(c -> connectionValidator.validate(c,
                            command.getDittoHeaders(), actorSystem), handleNullConnection(command));
            default -> {
            } // nothing to validate for other commands
        }
    }

    @Nonnull
    private static Runnable handleNullConnection(final ConnectivityCommand<?> command) {
        return () -> {
            throw new IllegalStateException("connection=null for " + command);
        };
    }

    private static Optional<Connection> resolveConnection(@Nullable final Supplier<Connection> connectionSupplier) {
        return Optional.ofNullable(connectionSupplier).map(Supplier::get);
    }
}
