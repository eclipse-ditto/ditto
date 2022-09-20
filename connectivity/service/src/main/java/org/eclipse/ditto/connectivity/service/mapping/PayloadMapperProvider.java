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
package org.eclipse.ditto.connectivity.service.mapping;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.ditto.internal.utils.extension.DittoExtensionIds;
import org.eclipse.ditto.internal.utils.extension.DittoExtensionPoint;

import akka.actor.ActorSystem;
import com.typesafe.config.Config;

/**
 * Extension to provide all configured {@link PayloadMapperFactory} implementations.
 */
public final class PayloadMapperProvider implements DittoExtensionPoint {

    private static final String PAYLOAD_MAPPERS = "payload-mappers";

    private final Set<PayloadMapperFactory> payloadMapperFactories;

    @SuppressWarnings("unused")
    private PayloadMapperProvider(final ActorSystem actorSystem, final Config config) {
        final DittoExtensionIds dittoExtensionIds = DittoExtensionIds.get(actorSystem);
        payloadMapperFactories = config.getList(PAYLOAD_MAPPERS)
                .stream()
                .map(configValue -> DittoExtensionPoint.ExtensionId.ExtensionIdConfig.of(PayloadMapperFactory.class, configValue))
                .map(extensionIdConfig -> dittoExtensionIds.computeIfAbsent(extensionIdConfig,
                        PayloadMapperFactory.ExtensionId::new))
                .map(extensionId -> extensionId.get(actorSystem))
                .collect(Collectors.toSet());
    }

    public Set<PayloadMapperFactory> getPayloadMapperFactories() {
        return payloadMapperFactories;
    }

    /**
     * Loads the implementation of {@code PayloadMapperProvider} which is configured for the
     * {@code ActorSystem}.
     *
     * @param actorSystem the actorSystem in which the {@code PayloadMapperProvider} should be loaded.
     * @param config the configuration for this extension.
     * @return the {@code PayloadMapperProvider} implementation.
     * @throws NullPointerException if {@code actorSystem} is {@code null}.
     */
    public static PayloadMapperProvider get(final ActorSystem actorSystem, final Config config) {
        checkNotNull(actorSystem, "actorSystem");
        checkNotNull(config, "config");
        final var extensionIdConfig = ExtensionId.computeConfig(config);
        return DittoExtensionIds.get(actorSystem)
                .computeIfAbsent(extensionIdConfig, ExtensionId::new)
                .get(actorSystem);
    }

    private static final class ExtensionId extends DittoExtensionPoint.ExtensionId<PayloadMapperProvider> {

        private static final String CONFIG_KEY = "payload-mapper-provider";

        private ExtensionId(final ExtensionIdConfig<PayloadMapperProvider> extensionIdConfig) {
            super(extensionIdConfig);
        }

        static ExtensionIdConfig<PayloadMapperProvider> computeConfig(final Config config) {
            return ExtensionIdConfig.of(PayloadMapperProvider.class, config, CONFIG_KEY);
        }

        @Override
        protected String getConfigKey() {
            return CONFIG_KEY;
        }

    }

}
