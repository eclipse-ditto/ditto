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

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.internal.utils.extension.DittoExtensionPoint;
import org.eclipse.ditto.internal.utils.extension.DittoExtensionIds;

import com.typesafe.config.Config;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;

/**
 * Creates actor {@link Props} based on the given {@link Connection}.
 */
public interface ClientActorPropsFactory extends DittoExtensionPoint {

    /**
     * Create actor {@link Props} for a connection.
     *
     * @param connection the connection.
     * @param commandForwarderActor the actor used to send signals into the ditto cluster..
     * @param connectionActor the connectionPersistenceActor which creates this client.
     * @param actorSystem the actorSystem.
     * @param dittoHeaders Ditto headers of the command that caused the client actors to be created.
     * @return the actor props
     */
    Props getActorPropsForType(Connection connection,
            ActorRef commandForwarderActor,
            ActorRef connectionActor,
            ActorSystem actorSystem,
            DittoHeaders dittoHeaders,
            Config connectivityConfigOverwrites);

    /**
     * Loads the implementation of {@code ClientActorPropsFactory} which is configured for the
     * {@code ActorSystem}.
     *
     * @param actorSystem the actorSystem in which the {@code ClientActorPropsFactory} should be loaded.
     * @param config the configuration of this extension.
     * @return the {@code ClientActorPropsFactory} implementation.
     * @throws NullPointerException if {@code actorSystem} is {@code null}.
     */
    static ClientActorPropsFactory get(final ActorSystem actorSystem, final Config config) {
        checkNotNull(actorSystem, "actorSystem");
        checkNotNull(config, "config");
        final var extensionIdConfig = ExtensionId.computeConfig(config);
        return DittoExtensionIds.get(actorSystem)
                .computeIfAbsent(extensionIdConfig, ExtensionId::new)
                .get(actorSystem);
    }

    final class ExtensionId extends DittoExtensionPoint.ExtensionId<ClientActorPropsFactory> {

        private static final String CONFIG_KEY = "client-actor-props-factory";

        private ExtensionId(final ExtensionIdConfig<ClientActorPropsFactory> extensionIdConfig) {
            super(extensionIdConfig);
        }

        static ExtensionIdConfig<ClientActorPropsFactory> computeConfig(final Config config) {
            return ExtensionIdConfig.of(ClientActorPropsFactory.class, config, CONFIG_KEY);
        }

        @Override
        protected String getConfigKey() {
            return CONFIG_KEY;
        }

    }

}
