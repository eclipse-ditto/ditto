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
package org.eclipse.ditto.connectivity.service.messaging.validation;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import org.eclipse.ditto.connectivity.model.signals.commands.ConnectivityCommandInterceptor;
import org.eclipse.ditto.internal.utils.extension.DittoExtensionPoint;
import org.eclipse.ditto.internal.utils.extension.DittoExtensionIds;


import com.typesafe.config.Config;

import akka.actor.ActorSystem;

public interface CustomConnectivityCommandInterceptorProvider extends DittoExtensionPoint {

    ConnectivityCommandInterceptor getCommandInterceptor();

    /**
     * Loads the implementation of {@code CustomConnectivityCommandInterceptorProvider} which is configured for the
     * {@code ActorSystem}.
     *
     * @param actorSystem the actorSystem in which the {@code CustomConnectivityCommandInterceptorProvider} should be loaded.
     * @param config the configuration of this extension.
     * @return the {@code CustomConnectivityCommandInterceptorProvider} implementation.
     * @throws NullPointerException if {@code actorSystem} is {@code null}.
     */
    static CustomConnectivityCommandInterceptorProvider get(final ActorSystem actorSystem, final Config config) {
        checkNotNull(actorSystem, "actorSystem");
        checkNotNull(config, "config");
        final var extensionIdConfig = ExtensionId.computeConfig(config);
        return DittoExtensionIds.get(actorSystem)
                .computeIfAbsent(extensionIdConfig, ExtensionId::new)
                .get(actorSystem);
    }

    final class ExtensionId extends DittoExtensionPoint.ExtensionId<CustomConnectivityCommandInterceptorProvider> {

        private static final String CONFIG_KEY = "custom-connectivity-command-interceptor-provider";

        private ExtensionId(final ExtensionIdConfig<CustomConnectivityCommandInterceptorProvider> extensionIdConfig) {
            super(extensionIdConfig);
        }

        static ExtensionIdConfig<CustomConnectivityCommandInterceptorProvider> computeConfig(final Config config) {
            return ExtensionIdConfig.of(CustomConnectivityCommandInterceptorProvider.class, config, CONFIG_KEY);
        }

        @Override
        protected String getConfigKey() {
            return CONFIG_KEY;
        }

    }

}
