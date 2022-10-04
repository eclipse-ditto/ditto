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

import org.eclipse.ditto.base.model.signals.events.Event;

import com.typesafe.config.Config;

import akka.actor.AbstractActor;
import akka.actor.Actor;
import akka.japi.pf.ReceiveBuilder;

/**
 * Behavior to modify this actor's {@link ConnectivityConfig} and register for changes to {@link ConnectivityConfig}.
 */
public interface ConnectivityConfigModifiedBehavior extends Actor {

     /**
     * Injectable behavior to handle an {@code Event} that transports config changes.
      * This involves modified Hub parameters (credentials) for 'Hono'-connections as well.
     *
     * @return behavior to handle an {@code Event} that transports config changes.
     */
    default AbstractActor.Receive connectivityConfigModifiedBehavior() {
        return ReceiveBuilder.create()
                .match(Event.class, event -> getConnectivityConfigProvider().canHandle(event), this::handleEvent)
                .build();
    }

    /**
     * Handles the received event by converting it to a {@link Config}.
     *
     * @param event the received event
     */
    default void handleEvent(final Event<?> event) {
        getConnectivityConfigProvider().handleEvent(event);
    }

    /**
     * @return a {@link ConnectionConfigProvider} required to register this actor for config changes
     */
    default ConnectionConfigProvider getConnectivityConfigProvider() {
        return ConnectionConfigProviderFactory.getInstance(context().system());
    }

}
