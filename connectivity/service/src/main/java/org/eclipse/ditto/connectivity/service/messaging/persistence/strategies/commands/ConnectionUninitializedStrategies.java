/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import java.util.Optional;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.signals.commands.exceptions.ConnectionNotAccessibleException;
import org.eclipse.ditto.connectivity.model.signals.events.ConnectivityEvent;
import org.eclipse.ditto.connectivity.service.messaging.persistence.stages.ConnectionState;
import org.eclipse.ditto.internal.utils.persistentactors.commands.AbstractCommandStrategy;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;

/**
 * Strategies to handle signals as an uninitialized connection that stashes every command.
 */
public final class ConnectionUninitializedStrategies
        extends AbstractCommandStrategy<Command<?>, Connection, ConnectionState, ConnectivityEvent<?>>
        implements ConnectivityCommandStrategies {

    private final Consumer<Command<?>> action;

    private ConnectionUninitializedStrategies(final Consumer<Command<?>> action) {
        super(Command.class);
        this.action = action;
    }

    /**
     * Return a new instance of this class.
     *
     * @param action what to do on connectivity commands.
     * @return the empty result.
     */
    public static ConnectionUninitializedStrategies of(final Consumer<Command<?>> action) {
        return new ConnectionUninitializedStrategies(action);
    }

    @Override
    public boolean isDefined(final Command<?> command) {
        return true;
    }

    @Override
    protected Optional<Metadata> calculateRelativeMetadata(@Nullable final Connection entity,
            final Command<?> command) {
        return Optional.empty();
    }

    @Override
    protected Result<ConnectivityEvent<?>> doApply(final Context<ConnectionState> context,
            @Nullable final Connection entity,
            final long nextRevision, final Command<?> command, @Nullable final Metadata metadata) {
        action.accept(command);
        return Result.empty();
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

}
