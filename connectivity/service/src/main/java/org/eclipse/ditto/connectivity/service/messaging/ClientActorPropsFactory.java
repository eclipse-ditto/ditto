/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.Connection;

import com.typesafe.config.Config;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;

/**
 * Creates actor {@link Props} based on the given {@link Connection}.
 */
public interface ClientActorPropsFactory {

    /**
     * Create actor {@link Props} for a connection.
     *
     * @param connection the connection.
     * @param proxyActor the actor used to send signals into the ditto cluster..
     * @param connectionActor the connectionPersistenceActor which creates this client.
     * @param actorSystem the actorSystem.
     * @param dittoHeaders Ditto headers of the command that caused the client actors to be created.
     * @return the actor props
     */
    Props getActorPropsForType(Connection connection, ActorRef proxyActor, ActorRef connectionActor,
            ActorSystem actorSystem, DittoHeaders dittoHeaders, Config connectivityConfigOverwrites);

}
