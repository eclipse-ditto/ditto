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
package org.eclipse.ditto.edge.service.dispatching;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.service.DittoExtensionIds;
import org.eclipse.ditto.base.service.DittoExtensionPoint;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;

/**
 * Extension which transforms a received {@link Signal} (at the "edge") to a CompletionStage of a transformed Signal,
 * e.g. in order to enhance the Signal.
 */
@FunctionalInterface
public interface SignalTransformer extends Function<Signal<?>, CompletionStage<Signal<?>>>, DittoExtensionPoint {

    /**
     * Loads the implementation of {@code SignalTransformer} which is configured for the
     * {@code ActorSystem}.
     *
     * @param actorSystem the actorSystem in which the {@code SignalTransformer} should be loaded.
     * @param config the configuration of this extension.
     * @return the {@code SignalTransformer} implementation.
     * @throws NullPointerException if {@code actorSystem} is {@code null}.
     */
    static SignalTransformer get(final ActorSystem actorSystem, final Config config) {
        checkNotNull(actorSystem, "actorSystem");
        checkNotNull(config, "config");
        final var extensionIdConfig = ExtensionId.computeConfig(config);
        return DittoExtensionIds.get(actorSystem)
                .computeIfAbsent(extensionIdConfig, ExtensionId::new)
                .get(actorSystem);
    }

    final class ExtensionId extends DittoExtensionPoint.ExtensionId<SignalTransformer> {

        private static final String CONFIG_KEY = "signal-transformer";
        private static final String CONFIG_PATH = "ditto.extensions." + CONFIG_KEY;

        private ExtensionId(final ExtensionIdConfig<SignalTransformer> extensionIdConfig) {
            super(extensionIdConfig);
        }

        static ExtensionIdConfig<SignalTransformer> computeConfig(final Config config) {
            return ExtensionIdConfig.of(SignalTransformer.class, config, CONFIG_KEY);
        }

        @Override
        protected String getConfigPath() {
            return CONFIG_PATH;
        }

    }
}
