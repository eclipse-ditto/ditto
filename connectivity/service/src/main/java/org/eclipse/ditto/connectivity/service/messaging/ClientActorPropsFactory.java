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

import java.util.List;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.service.DittoExtensionPoint;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.service.config.DittoConnectivityConfig;
import org.eclipse.ditto.internal.utils.akka.AkkaClassLoader;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;

import com.typesafe.config.Config;

import akka.actor.AbstractExtensionId;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.ExtendedActorSystem;
import akka.actor.Props;
/**
 * Creates actor {@link Props} based on the given {@link Connection}.
 */
public abstract class ClientActorPropsFactory implements DittoExtensionPoint {

    private static final ExtensionId EXTENSION_ID = new ExtensionId();

    protected final ActorSystem actorSystem;

    /**
     * @param actorSystem the actor system in which to load the extension.
     */
    protected ClientActorPropsFactory(final ActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

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
    public abstract Props getActorPropsForType(Connection connection,
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
    public static ClientActorPropsFactory get(final ActorSystem actorSystem) {
        return EXTENSION_ID.get(actorSystem);
    }

    private static final class ExtensionId extends AbstractExtensionId<ClientActorPropsFactory> {

        @Override
        public ClientActorPropsFactory createExtension(final ExtendedActorSystem system) {
            final var implementation =
                    DittoConnectivityConfig.of(DefaultScopedConfig.dittoScoped(
                            system.settings().config())).getConnectionConfig().getClientActorPropsFactory();

            return AkkaClassLoader.instantiate(system, ClientActorPropsFactory.class,
                    implementation,
                    List.of(ActorSystem.class),
                    List.of(system));
        }
    }

}
