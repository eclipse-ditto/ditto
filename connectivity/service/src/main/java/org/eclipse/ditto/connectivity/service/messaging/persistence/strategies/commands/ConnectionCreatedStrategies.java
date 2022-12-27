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
package org.eclipse.ditto.connectivity.service.messaging.persistence.strategies.commands;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.connectivity.api.commands.sudo.ConnectivitySudoCommand;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.signals.commands.ConnectivityCommand;
import org.eclipse.ditto.connectivity.model.signals.commands.exceptions.ConnectionNotAccessibleException;
import org.eclipse.ditto.connectivity.model.signals.events.ConnectivityEvent;
import org.eclipse.ditto.connectivity.service.messaging.persistence.stages.ConnectionState;
import org.eclipse.ditto.internal.utils.persistentactors.commands.AbstractCommandStrategies;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;

import akka.actor.ActorSystem;

/**
 * Strategies to handle signals as an existing connection.
 */
public final class ConnectionCreatedStrategies
        extends AbstractCommandStrategies<Command<?>, Connection, ConnectionState, ConnectivityEvent<?>>
        implements ConnectivityCommandStrategies {

    private ConnectionCreatedStrategies() {
        super(Command.class);
    }

    /**
     * @param actorSystem Actor system reference
     * @return the unique instance of this class.
     */
    public static ConnectionCreatedStrategies getInstance(final ActorSystem actorSystem) {
        return newCreatedStrategies(actorSystem);
    }

    private static ConnectionCreatedStrategies newCreatedStrategies(final ActorSystem actorSystem) {
        final ConnectionCreatedStrategies strategies = new ConnectionCreatedStrategies();
        strategies.addStrategy(new StagedCommandStrategy());
        strategies.addStrategy(new TestConnectionConflictStrategy());
        strategies.addStrategy(new ConnectionConflictStrategy());
        strategies.addStrategy(new ModifyConnectionStrategy());
        strategies.addStrategy(new DeleteConnectionStrategy());
        strategies.addStrategy(new OpenConnectionStrategy());
        strategies.addStrategy(new CloseConnectionStrategy());
        strategies.addStrategy(new ResetConnectionMetricsStrategy());
        strategies.addStrategy(new EnableConnectionLogsStrategy());
        strategies.addStrategy(new RetrieveConnectionLogsStrategy());
        strategies.addStrategy(new ResetConnectionLogsStrategy());
        strategies.addStrategy(new RetrieveConnectionStrategy());
        strategies.addStrategy(new RetrieveResolvedHonoConnectionStrategy(actorSystem));
        strategies.addStrategy(new RetrieveConnectionStatusStrategy());
        strategies.addStrategy(new RetrieveConnectionMetricsStrategy());
        strategies.addStrategy(new LoggingExpiredStrategy());
        strategies.addStrategy(new SudoRetrieveConnectionTagsStrategy());
        strategies.addStrategy(new SudoAddConnectionLogEntryStrategy());
        return strategies;
    }

    @Override
    public boolean isDefined(final Command<?> command) {
        return command instanceof ConnectivityCommand || command instanceof ConnectivitySudoCommand;
    }

    @Override
    public Result<ConnectivityEvent<?>> unhandled(final Context<ConnectionState> context,
            @Nullable final Connection entity,
            final long nextRevision,
            final Command<?> command) {

        return ResultFactory.newErrorResult(ConnectionNotAccessibleException
                .newBuilder(context.getState().id())
                .dittoHeaders(command.getDittoHeaders())
                .build(), command);
    }

    @Override
    protected Result<ConnectivityEvent<?>> getEmptyResult() {
        return ResultFactory.emptyResult();
    }

}
