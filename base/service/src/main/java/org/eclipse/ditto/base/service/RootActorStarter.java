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
package org.eclipse.ditto.base.service;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import org.eclipse.ditto.internal.utils.extension.DittoExtensionIds;
import org.eclipse.ditto.internal.utils.extension.DittoExtensionPoint;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;

/**
 * Extension to start custom root actors in service.
 *
 * @since 3.0.0
 */
public interface RootActorStarter extends DittoExtensionPoint {

    /**
     * Execute custom custom code.
     */
    void execute();

    /**
     * Loads the implementation of {@code RootActorStarter} which is configured for the
     * {@code ActorSystem}.
     *
     * @param actorSystem the actorSystem in which the {@code RootActorStarter} should be loaded.
     * @param config the configuration of this extension.
     * @return the {@code RootActorStarter} implementation.
     * @throws NullPointerException if {@code actorSystem} is {@code null}.
     */
    static RootActorStarter get(final ActorSystem actorSystem, final Config config) {
        checkNotNull(actorSystem, "actorSystem");
        checkNotNull(config, "config");
        final var extensionIdConfig = ExtensionId.computeConfig(config);
        return DittoExtensionIds.get(actorSystem)
                .computeIfAbsent(extensionIdConfig, ExtensionId::new)
                .get(actorSystem);
    }

    final class ExtensionId extends DittoExtensionPoint.ExtensionId<RootActorStarter> {

        private static final String CONFIG_KEY = "root-actor-starter";

        private ExtensionId(final ExtensionIdConfig<RootActorStarter> extensionIdConfig) {
            super(extensionIdConfig);
        }

        static ExtensionIdConfig<RootActorStarter> computeConfig(final Config config) {
            return ExtensionIdConfig.of(RootActorStarter.class, config, CONFIG_KEY);
        }

        @Override
        protected String getConfigKey() {
            return CONFIG_KEY;
        }

    }

}
