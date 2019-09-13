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
import static org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionAction.BECOME_DELETED;
import static org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionAction.CLOSE_CONNECTION;
import static org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionAction.DISABLE_LOGGING;
import static org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionAction.ENABLE_LOGGING;
import static org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionAction.OPEN_CONNECTION;
import static org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionAction.PERSIST_AND_APPLY_EVENT;
import static org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionAction.RETRIEVE_CONNECTION_LOGS;
import static org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionAction.RETRIEVE_CONNECTION_METRICS;
import static org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionAction.RETRIEVE_CONNECTION_STATUS;
import static org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionAction.SEND_RESPONSE;
import static org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionAction.TELL_CLIENT_ACTORS_IF_STARTED;
import static org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionAction.UPDATE_SUBSCRIPTIONS;
import static org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory.newErrorResult;
import static org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory.newMutationResult;
import static org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory.newQueryResult;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectivityStatus;
import org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionAction;
import org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionState;
import org.eclipse.ditto.services.connectivity.messaging.persistence.stages.StagedCommand;
import org.eclipse.ditto.services.utils.persistentactors.commands.AbstractCommandStrategy;
import org.eclipse.ditto.services.utils.persistentactors.commands.AbstractReceiveStrategy;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.connectivity.ConnectivityCommand;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionConflictException;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionNotAccessibleException;
import org.eclipse.ditto.signals.commands.connectivity.modify.CloseConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.CloseConnectionResponse;
import org.eclipse.ditto.signals.commands.connectivity.modify.CreateConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.DeleteConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.DeleteConnectionResponse;
import org.eclipse.ditto.signals.commands.connectivity.modify.EnableConnectionLogs;
import org.eclipse.ditto.signals.commands.connectivity.modify.EnableConnectionLogsResponse;
import org.eclipse.ditto.signals.commands.connectivity.modify.LoggingExpired;
import org.eclipse.ditto.signals.commands.connectivity.modify.ModifyConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.ModifyConnectionResponse;
import org.eclipse.ditto.signals.commands.connectivity.modify.OpenConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.OpenConnectionResponse;
import org.eclipse.ditto.signals.commands.connectivity.modify.ResetConnectionLogs;
import org.eclipse.ditto.signals.commands.connectivity.modify.ResetConnectionLogsResponse;
import org.eclipse.ditto.signals.commands.connectivity.modify.ResetConnectionMetrics;
import org.eclipse.ditto.signals.commands.connectivity.modify.ResetConnectionMetricsResponse;
import org.eclipse.ditto.signals.commands.connectivity.modify.TestConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.TestConnectionResponse;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnection;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionLogs;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionMetrics;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionResponse;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionStatus;
import org.eclipse.ditto.signals.events.connectivity.ConnectionClosed;
import org.eclipse.ditto.signals.events.connectivity.ConnectionDeleted;
import org.eclipse.ditto.signals.events.connectivity.ConnectionModified;
import org.eclipse.ditto.signals.events.connectivity.ConnectionOpened;
import org.eclipse.ditto.signals.events.connectivity.ConnectivityEvent;

// TODO
public class ConnectionCreatedStrategies
        extends AbstractReceiveStrategy<Signal, Connection, ConnectionState, Result<ConnectivityEvent>> {

    private static final ConnectionCreatedStrategies CREATED_STRATEGIES = newCreatedStrategies();

    private ConnectionCreatedStrategies() {
        super(Signal.class);
    }

    // TODO
    public static ConnectionCreatedStrategies getInstance() {
        return CREATED_STRATEGIES;
    }

    private static ConnectionCreatedStrategies newCreatedStrategies() {
        final ConnectionCreatedStrategies strategies = new ConnectionCreatedStrategies();
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
        strategies.addStrategy(new RetrieveConnectionStatusStrategy());
        strategies.addStrategy(new RetrieveConnectionMetricsStrategy());
        strategies.addStrategy(new LoggingExpiredStrategy());
        return strategies;
    }

    @Override
    public Result<ConnectivityEvent> unhandled(final Context<ConnectionState> context,
            @Nullable final Connection entity,
            final long nextRevision,
            final Signal signal) {

        // unhandled signal when created: forward to client actors in connection actor.
        final StagedCommand forward = StagedCommand.forwardSignal(signal);
        return ResultFactory.newMutationResult(forward, forward.getEvent(), forward.getResponse());
    }

    @Override
    protected Result<ConnectivityEvent> getEmptyResult() {
        return ResultFactory.emptyResult();
    }

    private abstract static class AbstractStrategy<C>
            extends AbstractCommandStrategy<C, Connection, ConnectionState, Result<ConnectivityEvent>> {

        AbstractStrategy(final Class<C> theMatchingClass) {
            super(theMatchingClass);
        }

        @Override
        public boolean isDefined(final C command) {
            return true;
        }

        ConnectionNotAccessibleException notAccessible(final Context<ConnectionState> context,
                final WithDittoHeaders command) {
            return ConnectionNotAccessibleException.newBuilder(context.getState().id())
                    .dittoHeaders(command.getDittoHeaders())
                    .build();
        }
    }

    private abstract static class AbstractSingleActionStrategy<C extends ConnectivityCommand>
            extends AbstractStrategy<C> {

        AbstractSingleActionStrategy(final Class<C> theMatchingClass) {
            super(theMatchingClass);
        }

        abstract ConnectionAction getAction();

        @Override
        protected Result<ConnectivityEvent> doApply(final Context<ConnectionState> context,
                @Nullable final Connection connection, final long nextRevision, final C command) {
            final ConnectivityEvent event = StagedCommand.dummyEvent();
            final Collection<ConnectionAction> actions = Collections.singletonList(getAction());
            return newMutationResult(StagedCommand.of(command, event, command, actions), event, command);
        }
    }

    private abstract static class AbstractEphemeralStrategy<C extends ConnectivityCommand>
            extends AbstractStrategy<C> {


        AbstractEphemeralStrategy(final Class<C> theMatchingClass) {
            super(theMatchingClass);
        }

        abstract WithDittoHeaders getResponse(final ConnectionState connectionId, final DittoHeaders headers);

        abstract Collection<ConnectionAction> getActions();

        @Override
        protected Result<ConnectivityEvent> doApply(final Context<ConnectionState> context,
                @Nullable final Connection connection, final long nextRevision, final C command) {
            final ConnectivityEvent event = StagedCommand.dummyEvent();
            final WithDittoHeaders response = getResponse(context.getState(), command.getDittoHeaders());
            final Collection<ConnectionAction> actions = getActions();
            return newMutationResult(StagedCommand.of(command, event, response, actions), event, response);
        }
    }


    private static final class TestConnectionConflictStrategy extends AbstractStrategy<TestConnection> {

        private TestConnectionConflictStrategy() {
            super(TestConnection.class);
        }

        @Override
        protected Result<ConnectivityEvent> doApply(final Context<ConnectionState> context,
                @Nullable final Connection entity, final long nextRevision, final TestConnection command) {
            return newQueryResult(command,
                    TestConnectionResponse.alreadyCreated(context.getState().id(), command.getDittoHeaders()));
        }
    }

    private static final class ConnectionConflictStrategy extends AbstractStrategy<CreateConnection> {

        private ConnectionConflictStrategy() {
            super(CreateConnection.class);
        }

        @Override
        protected Result<ConnectivityEvent> doApply(final Context<ConnectionState> context,
                @Nullable final Connection entity, final long nextRevision, final CreateConnection command) {
            context.getLog().info("Connection <{}> already exists! Responding with conflict.", context.getState().id());
            final ConnectionConflictException conflictException =
                    ConnectionConflictException.newBuilder(context.getState().id())
                            .dittoHeaders(command.getDittoHeaders())
                            .build();
            return newErrorResult(conflictException);
        }
    }

    private static final class ModifyConnectionStrategy extends AbstractStrategy<ModifyConnection> {

        private ModifyConnectionStrategy() {
            super(ModifyConnection.class);
        }

        @Override
        protected Result<ConnectivityEvent> doApply(final Context<ConnectionState> context,
                @Nullable final Connection entity, final long nextRevision, final ModifyConnection command) {
            final Connection connection = command.getConnection().toBuilder().lifecycle(ACTIVE).build();
            final ConnectivityEvent event =
                    ConnectionModified.of(connection, getEventTimestamp(), command.getDittoHeaders());
            final WithDittoHeaders response =
                    ModifyConnectionResponse.of(context.getState().id(), command.getDittoHeaders());
            final boolean isCurrentConnectionOpen = Optional.ofNullable(entity)
                    .map(c -> c.getConnectionStatus() == ConnectivityStatus.OPEN)
                    .orElse(false);
            final boolean isNextConnectionOpen = connection.getConnectionStatus() == ConnectivityStatus.OPEN;
            if (isNextConnectionOpen || isCurrentConnectionOpen) {
                final Collection<ConnectionAction> actions;
                if (isNextConnectionOpen) {
                    actions = Arrays.asList(PERSIST_AND_APPLY_EVENT, CLOSE_CONNECTION, OPEN_CONNECTION,
                            UPDATE_SUBSCRIPTIONS, SEND_RESPONSE);
                } else {
                    actions = Arrays.asList(PERSIST_AND_APPLY_EVENT, UPDATE_SUBSCRIPTIONS, CLOSE_CONNECTION,
                            SEND_RESPONSE);
                }
                return newMutationResult(StagedCommand.of(command, event, response, actions), event, response);
            } else {
                return newMutationResult(command, event, response);
            }
        }
    }

    private static final class OpenConnectionStrategy extends AbstractStrategy<OpenConnection> {

        private OpenConnectionStrategy() {
            super(OpenConnection.class);
        }

        @Override
        protected Result<ConnectivityEvent> doApply(final Context<ConnectionState> context,
                @Nullable final Connection connection, final long nextRevision, final OpenConnection command) {
            final ConnectivityEvent event = ConnectionOpened.of(context.getState().id(), command.getDittoHeaders());
            final WithDittoHeaders response =
                    OpenConnectionResponse.of(context.getState().id(), command.getDittoHeaders());
            final Collection<ConnectionAction> actions =
                    Arrays.asList(PERSIST_AND_APPLY_EVENT, OPEN_CONNECTION, UPDATE_SUBSCRIPTIONS, SEND_RESPONSE);
            return newMutationResult(StagedCommand.of(command, event, response, actions), event, response);
        }
    }

    private static final class CloseConnectionStrategy extends AbstractStrategy<CloseConnection> {

        private CloseConnectionStrategy() {
            super(CloseConnection.class);
        }

        @Override
        protected Result<ConnectivityEvent> doApply(final Context<ConnectionState> context,
                @Nullable final Connection connection, final long nextRevision, final CloseConnection command) {
            final ConnectivityEvent event = ConnectionClosed.of(context.getState().id(), command.getDittoHeaders());
            final WithDittoHeaders response =
                    CloseConnectionResponse.of(context.getState().id(), command.getDittoHeaders());
            final Collection<ConnectionAction> actions =
                    Arrays.asList(PERSIST_AND_APPLY_EVENT, UPDATE_SUBSCRIPTIONS, CLOSE_CONNECTION, SEND_RESPONSE);
            return newMutationResult(StagedCommand.of(command, event, response, actions), event, response);
        }
    }

    private static final class DeleteConnectionStrategy extends AbstractStrategy<DeleteConnection> {

        private DeleteConnectionStrategy() {
            super(DeleteConnection.class);
        }

        @Override
        protected Result<ConnectivityEvent> doApply(final Context<ConnectionState> context,
                @Nullable final Connection connection, final long nextRevision, final DeleteConnection command) {
            final ConnectivityEvent event = ConnectionDeleted.of(context.getState().id(), command.getDittoHeaders());
            final WithDittoHeaders response =
                    DeleteConnectionResponse.of(context.getState().id(), command.getDittoHeaders());
            final Collection<ConnectionAction> actions =
                    Arrays.asList(PERSIST_AND_APPLY_EVENT, CLOSE_CONNECTION, SEND_RESPONSE, BECOME_DELETED);
            return newMutationResult(StagedCommand.of(command, event, response, actions), event, response);
        }
    }

    private static final class ResetConnectionMetricsStrategy
            extends AbstractEphemeralStrategy<ResetConnectionMetrics> {

        private ResetConnectionMetricsStrategy() {
            super(ResetConnectionMetrics.class);
        }

        @Override
        WithDittoHeaders getResponse(final ConnectionState state, final DittoHeaders headers) {
            return ResetConnectionMetricsResponse.of(state.id(), headers);
        }

        @Override
        Collection<ConnectionAction> getActions() {
            return Arrays.asList(TELL_CLIENT_ACTORS_IF_STARTED, SEND_RESPONSE);
        }
    }

    private static final class EnableConnectionLogsStrategy extends AbstractEphemeralStrategy<EnableConnectionLogs> {

        private EnableConnectionLogsStrategy() {
            super(EnableConnectionLogs.class);
        }

        @Override
        WithDittoHeaders getResponse(final ConnectionState state, final DittoHeaders headers) {
            return EnableConnectionLogsResponse.of(state.id(), headers);
        }

        @Override
        Collection<ConnectionAction> getActions() {
            return Arrays.asList(TELL_CLIENT_ACTORS_IF_STARTED, SEND_RESPONSE, ENABLE_LOGGING);
        }
    }

    private static final class RetrieveConnectionLogsStrategy
            extends AbstractSingleActionStrategy<RetrieveConnectionLogs> {

        private RetrieveConnectionLogsStrategy() {
            super(RetrieveConnectionLogs.class);
        }

        @Override
        ConnectionAction getAction() {
            return RETRIEVE_CONNECTION_LOGS;
        }
    }

    private static final class ResetConnectionLogsStrategy extends AbstractEphemeralStrategy<ResetConnectionLogs> {

        private ResetConnectionLogsStrategy() {
            super(ResetConnectionLogs.class);
        }

        @Override
        WithDittoHeaders getResponse(final ConnectionState state, final DittoHeaders headers) {
            return ResetConnectionLogsResponse.of(state.id(), headers);
        }

        @Override
        Collection<ConnectionAction> getActions() {
            return Arrays.asList(TELL_CLIENT_ACTORS_IF_STARTED, SEND_RESPONSE);
        }
    }

    private static final class RetrieveConnectionStrategy extends AbstractStrategy<RetrieveConnection> {

        private RetrieveConnectionStrategy() {
            super(RetrieveConnection.class);
        }

        @Override
        protected Result<ConnectivityEvent> doApply(final Context<ConnectionState> context,
                @Nullable final Connection entity, final long nextRevision, final RetrieveConnection command) {
            if (entity != null) {
                return ResultFactory.newQueryResult(command,
                        RetrieveConnectionResponse.of(entity.toJson(), command.getDittoHeaders()));
            } else {
                return ResultFactory.newErrorResult(notAccessible(context, command));
            }
        }
    }

    private static final class RetrieveConnectionStatusStrategy
            extends AbstractSingleActionStrategy<RetrieveConnectionStatus> {

        private RetrieveConnectionStatusStrategy() {
            super(RetrieveConnectionStatus.class);
        }

        @Override
        ConnectionAction getAction() {
            return RETRIEVE_CONNECTION_STATUS;
        }
    }

    private static final class RetrieveConnectionMetricsStrategy
            extends AbstractSingleActionStrategy<RetrieveConnectionMetrics> {

        private RetrieveConnectionMetricsStrategy() {
            super(RetrieveConnectionMetrics.class);
        }

        @Override
        ConnectionAction getAction() {
            return RETRIEVE_CONNECTION_METRICS;
        }
    }

    private static final class LoggingExpiredStrategy
            extends AbstractSingleActionStrategy<LoggingExpired> {

        private LoggingExpiredStrategy() {
            super(LoggingExpired.class);
        }

        @Override
        ConnectionAction getAction() {
            return DISABLE_LOGGING;
        }
    }
}
