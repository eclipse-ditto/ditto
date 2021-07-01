/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.ditto.connectivity.service.config;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

import org.atteo.classindex.IndexSubclasses;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.service.mapping.ConnectionContext;
import org.eclipse.ditto.connectivity.service.mapping.DittoConnectionContext;

import akka.actor.ActorRef;

/**
 * Provides methods to load {@link ConnectivityConfig} and register for changes to {@link ConnectivityConfig}.
 */
@IndexSubclasses
public interface ConnectionContextProvider {

    /**
     * Loads a {@link ConnectivityConfig} by a connection ID.
     *
     * @param connectionId the connection id for which to load the {@link ConnectivityConfig}
     * @param dittoHeaders the ditto headers for which to load the {@link ConnectivityConfig}
     * @return the future connectivity config
     */
    CompletionStage<ConnectivityConfig> getConnectivityConfig(ConnectionId connectionId, DittoHeaders dittoHeaders);

    /**
     * Loads a {@link org.eclipse.ditto.connectivity.service.mapping.ConnectionContext} by a connection.
     *
     * @param connection the connection for which to load the connection context.
     * @param dittoHeaders the ditto headers for which to load the connection context.
     * @return the future connectivity context
     */
    default CompletionStage<ConnectionContext> getConnectionContext(final Connection connection,
            final DittoHeaders dittoHeaders) {
        return getConnectivityConfig(connection.getId(), dittoHeaders)
                .thenApply(config -> DittoConnectionContext.of(connection, config));
    }

    /**
     * Loads a connection context.
     *
     * @param connection the connection for which to load the connection context.
     * @return the connection context.
     */
    default CompletionStage<ConnectionContext> getConnectionContext(final Connection connection) {
        return getConnectionContext(connection, DittoHeaders.empty());
    }

    /**
     * Register the given {@code subscriber} for changes to the {@link ConnectivityConfig} of the given {@code
     * connectionId}. The given {@link ActorRef} will receive {@link Event}s to build the modified
     * {@link ConnectivityConfig}.
     *
     * @param connectionId the connection id
     * @param dittoHeaders the ditto headers
     * @param subscriber the subscriber that will receive {@link org.eclipse.ditto.base.model.signals.events.Event}s
     * @return a future that succeeds or fails depending on whether registration was successful.
     */
    CompletionStage<Void> registerForConnectivityConfigChanges(ConnectionId connectionId,
            DittoHeaders dittoHeaders, ActorRef subscriber);

    /**
     * Register the given {@code subscriber} for changes to the {@link ConnectivityConfig} of the given connection.
     * The given {@link ActorRef} will receive {@link Event}s to build the modified
     * {@link ConnectivityConfig}.
     *
     * @param context context of the connection whose config changes are subscribed
     * @param subscriber the subscriber that will receive {@link org.eclipse.ditto.base.model.signals.events.Event}s
     * @return a future that succeeds or fails depends on whether registration was successful.
     */
    default CompletionStage<Void> registerForConnectivityConfigChanges(final ConnectionContext context,
            final ActorRef subscriber) {
        return registerForConnectivityConfigChanges(context.getConnection().getId(), DittoHeaders.empty(), subscriber);
    }

    /**
     * Returns {@code true} if the implementation can handle the given {@code event} to generate a modified {@link
     * ConnectivityConfig} when passed to {@link #handleEvent(Event)}.
     *
     * @param event the event that may be used to generate modified config
     * @return {@code true} if the event is compatible
     */
    boolean canHandle(Event<?> event);

    /**
     * Uses the given {@code event} to create a modified {@link ConnectivityConfig}.
     *
     * @param event the event used to create a new {@link ConnectivityConfig}
     * @return Optional of the modified {@link ConnectivityConfig} or an empty Optional.
     */
    Optional<ConnectivityConfig> handleEvent(Event<?> event);

}
