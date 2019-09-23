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
package org.eclipse.ditto.services.connectivity.messaging.persistence.strategies.commands;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionState;
import org.eclipse.ditto.services.utils.persistentactors.commands.AbstractCommandStrategies;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.signals.commands.connectivity.ConnectivityCommand;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionNotAccessibleException;
import org.eclipse.ditto.signals.events.connectivity.ConnectivityEvent;

/**
 * Strategies to handle signals as a nonexistent connection.
 */
public class ConnectionDeletedStrategies
        extends AbstractCommandStrategies<ConnectivityCommand, Connection, ConnectionState, Result<ConnectivityEvent>> {

    private static final ConnectionDeletedStrategies DELETED_STRATEGIES = newDeletedStrategies();

    private ConnectionDeletedStrategies() {
        super(ConnectivityCommand.class);
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
    public Result<ConnectivityEvent> unhandled(final Context<ConnectionState> context,
            @Nullable final Connection entity,
            final long nextRevision,
            final ConnectivityCommand command) {

        context.getLog().warning("Received command for deleted connection, rejecting: <{}>", command);
        return ResultFactory.newErrorResult(ConnectionNotAccessibleException.newBuilder(context.getState().id())
                .dittoHeaders(command.getDittoHeaders())
                .build());
    }

    @Override
    public boolean isDefined(final ConnectivityCommand command) {
        // always defined so as to log ignored signals on debug level.
        return true;
    }

    @Override
    protected Result<ConnectivityEvent> getEmptyResult() {
        return ResultFactory.emptyResult();
    }

}
