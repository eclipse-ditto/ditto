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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.List;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.service.DittoExtensionPoint;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.internal.utils.akka.AkkaClassLoader;

import com.typesafe.config.Config;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;

/**
 * Creates actor {@link Props} based on the given {@link Connection}.
 */
public interface ClientActorPropsFactory extends DittoExtensionPoint {

    String CONFIG_PATH = "ditto.connectivity.connection.client-actor-props-factory";

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
    Props getActorPropsForType(Connection connection,
            ActorRef proxyActor,
            ActorRef connectionActor,
            ActorSystem actorSystem,
            DittoHeaders dittoHeaders,
            Config connectivityConfigOverwrites);

    /**
     * Loads the implementation of {@code ClientActorPropsFactory} which is configured for the
     * {@code ActorSystem}.
     *
     * @param actorSystem the actorSystem in which the {@code ClientActorPropsFactory} should be loaded.
     * @return the {@code ClientActorPropsFactory} implementation.
     * @throws NullPointerException if {@code actorSystem} is {@code null}.
     */
    static ClientActorPropsFactory get(final ActorSystem actorSystem) {
        checkNotNull(actorSystem, "actorSystem");
        final var implementation = actorSystem.settings().config().getString(CONFIG_PATH);

        return AkkaClassLoader.instantiate(actorSystem, ClientActorPropsFactory.class,
                implementation,
                List.of(ActorSystem.class),
                List.of(actorSystem));
    }

}
