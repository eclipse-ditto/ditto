/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.gateway.service.endpoints.routes.sse;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import org.eclipse.ditto.gateway.service.streaming.actors.StreamSupervisor;
import org.eclipse.ditto.internal.utils.extension.DittoExtensionPoint;
import org.eclipse.ditto.internal.utils.extension.DittoExtensionIds;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;

/**
 * Provides the means to supervise a particular SSE connection.
 */
public interface SseConnectionSupervisor extends DittoExtensionPoint, StreamSupervisor {

    /**
     * Loads the implementation of {@code SseConnectionSupervisor} which is configured for the
     * {@code ActorSystem}.
     *
     * @param actorSystem the actorSystem in which the {@code SseConnectionSupervisor} should be loaded.
     * @param config the config the extension is configured.
     * @return the {@code SseConnectionSupervisor} implementation.
     * @throws NullPointerException if {@code actorSystem} is {@code null}.
     * @since 3.0.0
     */
    static SseConnectionSupervisor get(final ActorSystem actorSystem, final Config config) {
        checkNotNull(actorSystem, "actorSystem");
        checkNotNull(config, "config");
        final var extensionIdConfig = ExtensionId.computeConfig(config);
        return DittoExtensionIds.get(actorSystem)
                .computeIfAbsent(extensionIdConfig, ExtensionId::new)
                .get(actorSystem);
    }

    final class ExtensionId extends DittoExtensionPoint.ExtensionId<SseConnectionSupervisor> {

        private static final String CONFIG_KEY = "sse-connection-supervisor";

        private ExtensionId(final ExtensionIdConfig<SseConnectionSupervisor> extensionIdConfig) {
            super(extensionIdConfig);
        }

        static ExtensionIdConfig<SseConnectionSupervisor> computeConfig(final Config config) {
            return ExtensionIdConfig.of(SseConnectionSupervisor.class, config, CONFIG_KEY);
        }

        @Override
        protected String getConfigKey() {
            return CONFIG_KEY;
        }

    }

}
