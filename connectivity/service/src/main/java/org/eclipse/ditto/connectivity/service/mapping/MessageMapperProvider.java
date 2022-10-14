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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.ditto.internal.utils.extension.DittoExtensionIds;
import org.eclipse.ditto.internal.utils.extension.DittoExtensionPoint;
import org.eclipse.ditto.internal.utils.extension.DittoExtensionPoint.ExtensionId.ExtensionIdConfig;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;

/**
 * Extension to provide all configured {@link MessageMapper} implementations.
 */
public final class MessageMapperProvider implements DittoExtensionPoint {

    private static final String MESSAGE_MAPPERS = "message-mappers";

    private final Map<String, MessageMapper> messageMappers;

    @SuppressWarnings("unused")
    private MessageMapperProvider(final ActorSystem actorSystem, final Config config) {
        final DittoExtensionIds dittoExtensionIds = DittoExtensionIds.get(actorSystem);
        final LinkedHashMap<String, MessageMapper> initMessageMappers = new LinkedHashMap<>();
        config.getList(MESSAGE_MAPPERS)
                .stream()
                .map(configValue -> ExtensionIdConfig.of(MessageMapper.class, configValue))
                .map(extensionIdConfig -> dittoExtensionIds.computeIfAbsent(extensionIdConfig,
                        MessageMapperExtensionId::new))
                .map(extensionId -> extensionId.get(actorSystem))
                .forEach(messageMapper -> {
                    initMessageMappers.put(messageMapper.getAlias(), messageMapper);
                    initMessageMappers.put(messageMapper.getClass().getName(), messageMapper);
                });
        messageMappers = Collections.unmodifiableMap(initMessageMappers);
    }

    /**
     * Provides the map of known and instantiated {@code MessageMapper}s.
     *
     * @return the map of known and instantiated {@code MessageMapper}s.
     */
    public Map<String, MessageMapper> getMessageMappers() {
        return messageMappers;
    }

    /**
     * Loads the implementation of {@code MessageMapperProvider} which is configured for the
     * {@code ActorSystem}.
     *
     * @param actorSystem the actorSystem in which the {@code MessageMapperProvider} should be loaded.
     * @param config the configuration for this extension.
     * @return the {@code MessageMapperProvider} implementation.
     * @throws NullPointerException if {@code actorSystem} is {@code null}.
     */
    public static MessageMapperProvider get(final ActorSystem actorSystem, final Config config) {
        checkNotNull(actorSystem, "actorSystem");
        checkNotNull(config, "config");
        final var extensionIdConfig = ExtensionId.computeConfig(config);
        return DittoExtensionIds.get(actorSystem)
                .computeIfAbsent(extensionIdConfig, ExtensionId::new)
                .get(actorSystem);
    }

    private static final class ExtensionId extends DittoExtensionPoint.ExtensionId<MessageMapperProvider> {

        private static final String CONFIG_KEY = "message-mapper-provider";

        private ExtensionId(final ExtensionIdConfig<MessageMapperProvider> extensionIdConfig) {
            super(extensionIdConfig);
        }

        static ExtensionIdConfig<MessageMapperProvider> computeConfig(final Config config) {
            return ExtensionIdConfig.of(MessageMapperProvider.class, config, CONFIG_KEY);
        }

        @Override
        protected String getConfigKey() {
            return CONFIG_KEY;
        }

    }

    private static final class MessageMapperExtensionId extends DittoExtensionPoint.ExtensionId<MessageMapper> {

        MessageMapperExtensionId(final ExtensionIdConfig<MessageMapper> extensionIdConfig) {
            super(extensionIdConfig);
        }

        @Override
        protected String getConfigKey() {
            throw new UnsupportedOperationException("MessageMappers do not support an individual config key. " +
                    "They should be configured in the ditto.extensions.message-mapper-provider.extension-config " +
                    "message-mappers list.");
        }

    }

}
