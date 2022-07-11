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

import akka.actor.ActorContext;
import akka.actor.ActorSystem;

/**
 * Extension to start custom child actors in root actor.
 *
 * @since 3.0.0
 */
public interface RootChildActorStarter extends DittoExtensionPoint {

    /**
     * Execute custom custom code.
     *
     * @param actorContext the context of the {@code RootActor}.
     */
    void execute(ActorContext actorContext);

    /**
     * Loads the implementation of {@code RootChildActorStarter} which is configured for the
     * {@code ActorSystem}.
     *
     * @param actorSystem the actorSystem in which the {@code RootChildActorStarter} should be loaded.
     * @param config the config the extension is configured.
     * @return the {@code RootChildActorStarter} implementation.
     * @throws NullPointerException if {@code actorSystem} is {@code null}.
     */
    static RootChildActorStarter get(final ActorSystem actorSystem, final Config config) {
        checkNotNull(actorSystem, "actorSystem");
        checkNotNull(config, "config");
        final var extensionIdConfig = ExtensionId.computeConfig(config);
        return DittoExtensionIds.get(actorSystem)
                .computeIfAbsent(extensionIdConfig, ExtensionId::new)
                .get(actorSystem);
    }

    final class ExtensionId extends DittoExtensionPoint.ExtensionId<RootChildActorStarter> {

        private static final String CONFIG_KEY = "root-child-actor-starter";

        private ExtensionId(final ExtensionIdConfig<RootChildActorStarter> extensionIdConfig) {
            super(extensionIdConfig);
        }

        static ExtensionIdConfig<RootChildActorStarter> computeConfig(final Config config) {
            return ExtensionIdConfig.of(RootChildActorStarter.class, config, CONFIG_KEY);
        }

        @Override
        protected String getConfigKey() {
            return CONFIG_KEY;
        }

    }

}
