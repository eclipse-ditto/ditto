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

/**
 * Strategies to handle signals as a nonexistent connection.
 */
public final class ConnectionDeletedStrategies
        extends AbstractCommandStrategies<Command<?>, Connection, ConnectionState, ConnectivityEvent<?>>
        implements ConnectivityCommandStrategies {

    private static final ConnectionDeletedStrategies DELETED_STRATEGIES = newDeletedStrategies();

    private ConnectionDeletedStrategies() {
        super(Command.class);
    }

    /**
     * @return the unique instance of this class.
     */
    public static ConnectionDeletedStrategies getInstance() {
        return DELETED_STRATEGIES;
    }

    private static ConnectionDeletedStrategies newDeletedStrategies() {
        final ConnectionDeletedStrategies strategies = new ConnectionDeletedStrategies();
        strategies.addStrategy(new StagedCommandStrategy());
        strategies.addStrategy(new TestConnectionStrategy());
        strategies.addStrategy(new CreateConnectionStrategy());
        return strategies;
    }

    @Override
    public Result<ConnectivityEvent<?>> unhandled(final Context<ConnectionState> context,
            @Nullable final Connection entity,
            final long nextRevision,
            final Command<?> command) {

        context.getLog().withCorrelationId(command)
                .warning("Received command for deleted connection, rejecting: <{}>", command);
        return ResultFactory.newErrorResult(ConnectionNotAccessibleException.newBuilder(context.getState().id())
                .dittoHeaders(command.getDittoHeaders())
                .build(), command);
    }

    @Override
    public boolean isDefined(final Command<?> command) {
        return command instanceof ConnectivityCommand ||command instanceof ConnectivitySudoCommand;
    }

    @Override
    protected Result<ConnectivityEvent<?>> getEmptyResult() {
        return ResultFactory.emptyResult();
    }

}
