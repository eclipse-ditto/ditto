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

package org.eclipse.ditto.connectivity.service.config;

import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.atteo.classindex.IndexSubclasses;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.connectivity.model.ConnectionId;

import com.typesafe.config.Config;

import akka.actor.ActorRef;

/**
 * Provides methods to load {@link ConnectivityConfig} and register for changes to {@link ConnectivityConfig}.
 */
@IndexSubclasses
public interface ConnectionConfigProvider {

    /**
     * Loads specific overwrites of connectivity for a given connection ID.
     *
     * @param connectionId the connection id for which to load config overwrites
     * @param dittoHeaders the DittoHeaders of the original command which woke up the connection supervisor actor.
     * @return the future config overwrites
     */
    CompletionStage<Config> getConnectivityConfigOverwrites(ConnectionId connectionId,
            @Nullable DittoHeaders dittoHeaders);

    /**
     * Register the given {@code subscriber} for changes to the {@link ConnectivityConfig} of the given {@code
     * connectionId}. The given {@link ActorRef} will receive {@link Event}s to build the modified
     * {@link ConnectivityConfig}.
     *
     * @param connectionId the connection id
     * @param dittoHeaders the DittoHeaders of the original command which woke up the connection supervisor actor.
     * @param subscriber the supervisor actor of the connection interested in these {@link org.eclipse.ditto.base.model.signals.events.Event}s
     * @return a future that succeeds or fails depending on whether registration was successful.
     */
    CompletionStage<Void> registerForConnectivityConfigChanges(ConnectionId connectionId,
            @Nullable DittoHeaders dittoHeaders, ActorRef subscriber);

    /**
     * Returns {@code true} if the implementation can handle the given {@code event} to generate a modified {@link ConnectivityConfig}
     * when passed to {@link #handleEvent(org.eclipse.ditto.base.model.signals.events.Event, akka.actor.ActorRef, akka.actor.ActorRef)}.
     *
     * @param event the event that may be used to generate modified config
     * @return {@code true} if the event is compatible
     */
    boolean canHandle(Event<?> event);

    /**
     * Uses the given {@code event} to create a config which should overwrite the default connectivity config.
     *
     * @param event the event used to invoke restart of the connection due to some changes in its configuration
     * @param supervisorActor the supervisor actor of the connection interested in these {@link Event}s
     * @param persistenceActor the persistence actor of the connection
     */
    void handleEvent(Event<?> event, ActorRef supervisorActor, @Nullable ActorRef persistenceActor);

}
