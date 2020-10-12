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
package org.eclipse.ditto.services.connectivity.messaging.amqp;

import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.services.connectivity.config.ConnectivityConfig;
import org.eclipse.ditto.services.connectivity.config.ConnectivityConfigBuildable;
import org.eclipse.ditto.services.connectivity.config.ConnectivityConfigProvider;

import akka.actor.AbstractActor;
import akka.actor.Actor;
import akka.japi.pf.ReceiveBuilder;

/**
 * TODO DG
 */
interface ConnectivityConfigModifiedBehavior extends Actor {

    default AbstractActor.Receive connectivityConfigModifiedBehavior() {
        return ReceiveBuilder.create()
                .match(ConnectivityConfigBuildable.class, connectivityConfigBuildable -> {
                    final ConnectivityConfig modifiedConnectivityConfig =
                            connectivityConfigBuildable.buildWith(getCurrentConnectivityConfig());
                    configModified(modifiedConnectivityConfig);
                })
                .build();
    }

    ConnectivityConfig getCurrentConnectivityConfig();

    ConnectivityConfigProvider getConnectivityConfigProvider();

    void configModified(ConnectivityConfig connectivityConfig);

    default void registerForConfigChanges(ConnectionId connectionId) {
        getConnectivityConfigProvider().registerForChanges(connectionId, self());
    }
}
