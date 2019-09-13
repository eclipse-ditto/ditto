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
package org.eclipse.ditto.services.connectivity.messaging;

import static org.eclipse.ditto.model.connectivity.ConnectionLifecycle.ACTIVE;
import static org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionAction.APPLY_EVENT;
import static org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionAction.BECOME_CREATED;
import static org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionAction.OPEN_CONNECTION_IGNORE_ERRORS;
import static org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionAction.PASSIVATE;
import static org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionAction.PERSIST_AND_APPLY_EVENT;
import static org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionAction.SEND_RESPONSE;
import static org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionAction.TEST_CONNECTION;
import static org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionAction.UPDATE_SUBSCRIPTIONS;
import static org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory.newErrorResult;
import static org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory.newMutationResult;
import static org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory.newQueryResult;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectivityStatus;
import org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionAction;
import org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionState;
import org.eclipse.ditto.services.connectivity.messaging.persistence.stages.StagedCommand;
import org.eclipse.ditto.services.connectivity.util.ConnectionLogUtil;
import org.eclipse.ditto.services.utils.persistentactors.commands.AbstractReceiveStrategy;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.connectivity.ConnectivityCommand;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionNotAccessibleException;
import org.eclipse.ditto.signals.commands.connectivity.modify.CreateConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.CreateConnectionResponse;
import org.eclipse.ditto.signals.commands.connectivity.modify.TestConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.TestConnectionResponse;
import org.eclipse.ditto.signals.events.connectivity.ConnectionCreated;
import org.eclipse.ditto.signals.events.connectivity.ConnectivityEvent;

// TODO
public class ConnectionDeletedStrategies
        extends AbstractReceiveStrategy<Signal, Connection, ConnectionState, Result<ConnectivityEvent>> {

    private static final ConnectionDeletedStrategies DELETED_STRATEGIES = newDeletedStrategies();

    private ConnectionDeletedStrategies() {
        super(Signal.class);
    }

    // TODO
    public static ConnectionDeletedStrategies getInstance() {
        return DELETED_STRATEGIES;
    }

    private static ConnectionDeletedStrategies newDeletedStrategies() {
        final ConnectionDeletedStrategies strategies = new ConnectionDeletedStrategies();
        strategies.addStrategy(new ConnectionCreatedStrategies.StagedCommandStrategy());
        strategies.addStrategy(new TestConnectionStrategy());
        strategies.addStrategy(new CreateConnectionStrategy());
        return strategies;
    }

    @Override
    public Result<ConnectivityEvent> unhandled(final Context<ConnectionState> context,
            @Nullable final Connection entity,
            final long nextRevision,
            final Signal signal) {

        if (signal instanceof ConnectivityCommand) {
            final ConnectivityCommand command = (ConnectivityCommand) signal;
            context.getLog().warning("Received command for deleted connection, rejecting: <{}>", command);
            return ResultFactory.newErrorResult(ConnectionNotAccessibleException.newBuilder(context.getState().id())
                    .dittoHeaders(signal.getDittoHeaders())
                    .build());
        } else {
            context.getLog().debug("Ignoring signal <{}> while deleted", signal);
            return ResultFactory.emptyResult();
        }
    }

    @Override
    protected Result<ConnectivityEvent> getEmptyResult() {
        return ResultFactory.emptyResult();
    }

    private static final class TestConnectionStrategy extends
            ConnectionCreatedStrategies.AbstractStrategy<TestConnection> {

        private TestConnectionStrategy() {
            super(TestConnection.class);
        }

        @Override
        protected Result<ConnectivityEvent> doApply(final Context<ConnectionState> context,
                @Nullable final Connection entity, final long nextRevision, final TestConnection command) {
            final Optional<DittoRuntimeException> validationError = ConnectionActor.validate(context, command);
            if (validationError.isPresent()) {
                return newErrorResult(validationError.get());
            } else if (entity == null) {
                final Connection connection = command.getConnection();
                final ConnectivityEvent event = ConnectionCreated.of(connection, command.getDittoHeaders());
                final Collection<ConnectionAction> actions =
                        Arrays.asList(APPLY_EVENT, TEST_CONNECTION, SEND_RESPONSE, PASSIVATE);
                final StagedCommand stagedCommand = StagedCommand.of(command, event, command, actions);
                return newMutationResult(stagedCommand, event, command);
            } else {
                return newQueryResult(command,
                        TestConnectionResponse.alreadyCreated(context.getState().id(), command.getDittoHeaders()));
            }
        }
    }

    private static final class CreateConnectionStrategy extends
            ConnectionCreatedStrategies.AbstractStrategy<CreateConnection> {

        private CreateConnectionStrategy() {
            super(CreateConnection.class);
        }

        @Override
        protected Result<ConnectivityEvent> doApply(final Context<ConnectionState> context,
                @Nullable final Connection entity, final long nextRevision, final CreateConnection command) {
            // TODO: see if enhancing log is needed anywhere else
            ConnectionLogUtil.enhanceLogWithCorrelationIdAndConnectionId(context.getLog(), command,
                    context.getState().id());
            final Connection connection = command.getConnection().toBuilder().lifecycle(ACTIVE).build();
            final ConnectivityEvent event =
                    ConnectionCreated.of(connection, getEventTimestamp(), command.getDittoHeaders());
            final WithDittoHeaders response =
                    CreateConnectionResponse.of(connection, command.getDittoHeaders());
            final Optional<DittoRuntimeException> validationError = ConnectionActor.validate(context, command);
            if (validationError.isPresent()) {
                return newErrorResult(validationError.get());
            } else if (connection.getConnectionStatus() == ConnectivityStatus.OPEN) {
                final Collection<ConnectionAction> actions = Arrays.asList(
                        PERSIST_AND_APPLY_EVENT, OPEN_CONNECTION_IGNORE_ERRORS, UPDATE_SUBSCRIPTIONS, SEND_RESPONSE,
                        BECOME_CREATED);
                return newMutationResult(StagedCommand.of(command, event, response, actions), event, response);
            } else {
                return newMutationResult(command, event, response, true, false);
            }
        }
    }

}
