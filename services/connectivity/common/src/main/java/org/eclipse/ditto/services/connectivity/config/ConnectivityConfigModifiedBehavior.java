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

import org.eclipse.ditto.model.connectivity.ConnectionId;

import akka.actor.AbstractActor;
import akka.actor.Actor;
import akka.japi.pf.ReceiveBuilder;

/**
 * Behavior to modify this actor's {@link ConnectivityConfig} and register for changes to {@link ConnectivityConfig}.
 */
public interface ConnectivityConfigModifiedBehavior extends Actor {

    /**
     * Injectable behavior to handle {@code ConnectivityConfigBuildable}.
     *
     * @return behavior to handle {@code ConnectivityConfigBuildable}.
     */
    default AbstractActor.Receive connectivityConfigModifiedBehavior() {
        return ReceiveBuilder.create()
                .match(ConnectivityConfigBuildable.class, connectivityConfigBuildable -> {
                    final ConnectivityConfig modifiedConnectivityConfig =
                            connectivityConfigBuildable.buildWith(getCurrentConnectivityConfig());
                    configModified(modifiedConnectivityConfig);
                })
                .build();
    }

    /**
     * Registers this actor for changes to connectivity config.
     *
     * @param connectionId the connection id
     */
    default void registerForConfigChanges(ConnectionId connectionId) {
        getConnectivityConfigProvider().registerForConnectivityConfigChanges(connectionId, self());
    }

    /**
     * @return the actor's current {@link ConnectivityConfig} required to merge with the received modifications
     */
    ConnectivityConfig getCurrentConnectivityConfig();

    /**
     * @return a {@link ConnectivityConfigProvider} required to register this actor for config changes
     */
    ConnectivityConfigProvider getConnectivityConfigProvider();

    /**
     * This method is called when a config modification is received.
     *
     * @param connectivityConfig the modified config
     */
    void configModified(ConnectivityConfig connectivityConfig);

}
