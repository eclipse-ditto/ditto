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

import static org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory.newMutationResult;

import java.util.List;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionAction;
import org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionState;
import org.eclipse.ditto.services.connectivity.messaging.persistence.stages.StagedCommand;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.signals.commands.connectivity.ConnectivityCommand;
import org.eclipse.ditto.signals.events.connectivity.ConnectivityEvent;

/**
 * Abstract base class for ephemeral strategies not affecting the persistence.
 *
 * @param <C> the type of the handled command
 */
abstract class AbstractEphemeralStrategy<C extends ConnectivityCommand> extends AbstractConnectivityCommandStrategy<C> {


    AbstractEphemeralStrategy(final Class<C> theMatchingClass) {
        super(theMatchingClass);
    }

    abstract WithDittoHeaders getResponse(final ConnectionState connectionId, final DittoHeaders headers);

    abstract List<ConnectionAction> getActions();

    @Override
    protected Result<ConnectivityEvent> doApply(final Context<ConnectionState> context,
            @Nullable final Connection connection, final long nextRevision, final C command) {
        final ConnectivityEvent event = StagedCommand.dummyEvent();
        final WithDittoHeaders response = getResponse(context.getState(), command.getDittoHeaders());
        final List<ConnectionAction> actions = getActions();
        return newMutationResult(StagedCommand.of(command, event, response, actions), event, response);
    }
}
