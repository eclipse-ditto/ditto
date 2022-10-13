/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.gateway.service.endpoints.actors;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import org.eclipse.ditto.internal.utils.extension.DittoExtensionIds;
import org.eclipse.ditto.internal.utils.extension.DittoExtensionPoint;

import com.typesafe.config.Config;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;

/**
 * Creates actor {@link akka.actor.Props}.
 */
public interface ConnectionsRetrievalActorPropsFactory extends DittoExtensionPoint {


    /**
     * Create Props object of an actor to handle 1
     * {@link org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnections} command.
     *
     * @param edgeCommandForwarder actor to forward commands to the appropriate service.
     * @param sender the sender of the {@code RetrieveConnections} command.
     * @return Props of the actor.
     */
    Props getActorProps(ActorRef edgeCommandForwarder, ActorRef sender);

    /**
     * Loads the implementation of {@code ClientActorPropsFactory} which is configured for the
     * {@code ActorSystem}.
     *
     * @param actorSystem the actorSystem in which the {@code ClientActorPropsFactory} should be loaded.
     * @param config the configuration of this extension.
     * @return the {@code ClientActorPropsFactory} implementation.
     * @throws NullPointerException if {@code actorSystem} is {@code null}.
     */
    static ConnectionsRetrievalActorPropsFactory get(final ActorSystem actorSystem, final Config config) {
        checkNotNull(actorSystem, "actorSystem");
        checkNotNull(config, "config");
        final var extensionIdConfig = ExtensionId.computeConfig(config);
        return DittoExtensionIds.get(actorSystem)
                .computeIfAbsent(extensionIdConfig, ExtensionId::new)
                .get(actorSystem);
    }

    final class ExtensionId extends DittoExtensionPoint.ExtensionId<ConnectionsRetrievalActorPropsFactory> {

        private static final String CONFIG_KEY = "connections-retrieval-actor-props-factory";

        private ExtensionId(final ExtensionIdConfig<ConnectionsRetrievalActorPropsFactory> extensionIdConfig) {
            super(extensionIdConfig);
        }

        static ExtensionIdConfig<ConnectionsRetrievalActorPropsFactory> computeConfig(final Config config) {
            return ExtensionIdConfig.of(ConnectionsRetrievalActorPropsFactory.class, config, CONFIG_KEY);
        }

        @Override
        protected String getConfigKey() {
            return CONFIG_KEY;
        }

    }

}
