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

package org.eclipse.ditto.services.connectivity.config;

import java.util.Optional;

import org.atteo.classindex.IndexSubclasses;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.signals.events.base.Event;

import akka.actor.ActorRef;

/**
 * Provides methods to load {@link ConnectivityConfig} and register for changes to {@link ConnectivityConfig}.
 */
@IndexSubclasses
public interface ConnectivityConfigProvider {

    /**
     * Loads a {@link ConnectivityConfig} by a connection ID.
     *
     * @param connectionId the connection id for which to load the {@link ConnectivityConfig}
     * @return the connectivity config
     */
    ConnectivityConfig getConnectivityConfig(ConnectionId connectionId);

    /**
     * Register the given {@code subscriber} for changes to the {@link ConnectivityConfig} of the given {@code
     * connectionId}. The given {@link ActorRef} will receive {@link Event}s to build the modified {@link
     * ConnectivityConfig}.
     *
     * @param connectionId the connection id
     * @param subscriber the subscriber that will receive {@link Event}s
     */
    void registerForConnectivityConfigChanges(ConnectionId connectionId, ActorRef subscriber);

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
