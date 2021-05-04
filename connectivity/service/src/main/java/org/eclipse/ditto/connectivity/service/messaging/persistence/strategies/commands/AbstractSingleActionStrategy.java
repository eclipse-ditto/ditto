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

import static org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory.newMutationResult;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.service.messaging.persistence.stages.ConnectionAction;
import org.eclipse.ditto.connectivity.service.messaging.persistence.stages.ConnectionState;
import org.eclipse.ditto.connectivity.service.messaging.persistence.stages.StagedCommand;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.connectivity.model.signals.commands.ConnectivityCommand;
import org.eclipse.ditto.connectivity.model.signals.events.ConnectivityEvent;

/**
 * Abstract base class for single action strategies.
 *
 * @param <C> the type of the handled command
 */
abstract class AbstractSingleActionStrategy<C extends ConnectivityCommand<?>>
        extends AbstractConnectivityCommandStrategy<C> {

    AbstractSingleActionStrategy(final Class<C> theMatchingClass) {
        super(theMatchingClass);
    }

    /**
     * @return the single action to undertake
     */
    abstract ConnectionAction getAction();

    @Override
    protected Result<ConnectivityEvent<?>> doApply(final Context<ConnectionState> context,
            @Nullable final Connection connection,
            final long nextRevision,
            final C command,
            @Nullable final Metadata metadata) {

        final List<ConnectionAction> actions = Collections.singletonList(getAction());
        return newMutationResult(StagedCommand.of(command, null, command, actions), null, command);
    }
}
