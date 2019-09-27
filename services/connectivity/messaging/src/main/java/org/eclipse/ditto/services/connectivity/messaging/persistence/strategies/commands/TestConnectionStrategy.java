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

import static org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionAction.APPLY_EVENT;
import static org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionAction.PASSIVATE;
import static org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionAction.SEND_RESPONSE;
import static org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionAction.TEST_CONNECTION;
import static org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory.newErrorResult;
import static org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory.newMutationResult;
import static org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory.newQueryResult;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionAction;
import org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionState;
import org.eclipse.ditto.services.connectivity.messaging.persistence.stages.StagedCommand;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.signals.commands.connectivity.modify.TestConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.TestConnectionResponse;
import org.eclipse.ditto.signals.events.connectivity.ConnectionCreated;
import org.eclipse.ditto.signals.events.connectivity.ConnectivityEvent;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.commands.connectivity.modify.TestConnection} command.
 */
final class TestConnectionStrategy extends AbstractConnectivityCommandStrategy<TestConnection> {

    TestConnectionStrategy() {
        super(TestConnection.class);
    }

    @Override
    protected Result<ConnectivityEvent> doApply(final Context<ConnectionState> context,
            @Nullable final Connection entity, final long nextRevision, final TestConnection command) {
        final Optional<DittoRuntimeException> validationError = validate(context, command);
        if (validationError.isPresent()) {
            return newErrorResult(validationError.get());
        } else if (entity == null) {
            final Connection connection = command.getConnection();
            final ConnectivityEvent event = ConnectionCreated.of(connection, command.getDittoHeaders());
            final List<ConnectionAction> actions =
                    Arrays.asList(APPLY_EVENT, TEST_CONNECTION, SEND_RESPONSE, PASSIVATE);
            final StagedCommand stagedCommand = StagedCommand.of(command, event, command, actions);
            return newMutationResult(stagedCommand, event, command);
        } else {
            return newQueryResult(command,
                    TestConnectionResponse.alreadyCreated(context.getState().id(), command.getDittoHeaders()));
        }
    }
}
