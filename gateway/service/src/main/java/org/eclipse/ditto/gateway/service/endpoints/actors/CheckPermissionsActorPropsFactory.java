/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
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

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Props;
import org.eclipse.ditto.internal.utils.extension.DittoExtensionIds;
import org.eclipse.ditto.internal.utils.extension.DittoExtensionPoint;

import com.typesafe.config.Config;

import java.time.Duration;

/**
 * Factory of props of actors that handle permission checks.
 */
@FunctionalInterface
public interface CheckPermissionsActorPropsFactory extends DittoExtensionPoint {

    /**
     * Create Props object of an actor to handle 1
     * {@link org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnections} command.
     *
     * @param edgeCommandForwarder actor to forward commands to the appropriate service.
     * @return Props of the actor.
     */
    Props getActorProps(ActorRef edgeCommandForwarder, ActorRef sender, Duration defaultTimeout);

    static CheckPermissionsActorPropsFactory get(final ActorSystem actorSystem, final Config config) {
        checkNotNull(actorSystem, "actorSystem");
        checkNotNull(config, "config");
        final var extensionIdConfig = ExtensionId.computeConfig(config);
        return DittoExtensionIds.get(actorSystem)
                .computeIfAbsent(extensionIdConfig, ExtensionId::new)
                .get(actorSystem);
    }

    final class ExtensionId extends DittoExtensionPoint.ExtensionId<CheckPermissionsActorPropsFactory> {

        private static final String CONFIG_KEY = "check-permissions-actor-props-factory";

        private ExtensionId(final ExtensionIdConfig<CheckPermissionsActorPropsFactory> extensionIdConfig) {
            super(extensionIdConfig);
        }

        static ExtensionIdConfig<CheckPermissionsActorPropsFactory> computeConfig(final Config config) {
            return ExtensionIdConfig.of(CheckPermissionsActorPropsFactory.class, config, CONFIG_KEY);
        }

        @Override
        protected String getConfigKey() {
            return CONFIG_KEY;
        }

    }

}

