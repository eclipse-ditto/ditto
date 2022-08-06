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
package org.eclipse.ditto.connectivity.service.enforcement;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.internal.utils.extension.DittoExtensionPoint;
import org.eclipse.ditto.internal.utils.extension.DittoExtensionIds;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;
import akka.actor.Props;

/**
 * Responsible to provide the props of the actor that should be used to enforce connection commands.
 */
public interface ConnectionEnforcerActorPropsFactory extends DittoExtensionPoint {

    /**
     * @param connectionId the ID of the connection for which the actor should enforce the commands.
     * @return the enforcer actor props.
     */
    Props get(ConnectionId connectionId);

    static ConnectionEnforcerActorPropsFactory get(final ActorSystem actorSystem, final Config config) {
        checkNotNull(actorSystem, "actorSystem");
        checkNotNull(config, "config");
        final var extensionIdConfig = ExtensionId.computeConfig(config);
        return DittoExtensionIds.get(actorSystem)
                .computeIfAbsent(extensionIdConfig, ExtensionId::new)
                .get(actorSystem);
    }

    final class ExtensionId extends DittoExtensionPoint.ExtensionId<ConnectionEnforcerActorPropsFactory> {

        private static final String CONFIG_KEY = "connection-enforcer-actor-props-factory";

        private ExtensionId(final ExtensionIdConfig<ConnectionEnforcerActorPropsFactory> extensionIdConfig) {
            super(extensionIdConfig);
        }

        static ExtensionIdConfig<ConnectionEnforcerActorPropsFactory> computeConfig(final Config config) {
            return ExtensionIdConfig.of(ConnectionEnforcerActorPropsFactory.class, config, CONFIG_KEY);
        }

        @Override
        protected String getConfigKey() {
            return CONFIG_KEY;
        }

    }

}
