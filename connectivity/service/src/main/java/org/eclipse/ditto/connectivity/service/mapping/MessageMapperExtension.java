/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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

import javax.annotation.Nullable;

import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.internal.utils.extension.DittoExtensionIds;
import org.eclipse.ditto.internal.utils.extension.DittoExtensionPoint;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;

/**
 * Interface for wrapping an existing message mapper after creation.
 */
public interface MessageMapperExtension extends DittoExtensionPoint {

    /**
     * Instantiates a message mapper.
     *
     * @param connectionId ID of the connection.
     * @param mapper the mapper that can be extended or wrapped.
     * @return an instantiated message mapper according to the mapping context if instantiation is possible, or
     * {@code null} otherwise.
     */
    @Nullable
    MessageMapper apply(ConnectionId connectionId, MessageMapper mapper);

    /**
     * Loads the implementation of {@code MessageMapperExtension} which is configured for the {@code ActorSystem}.
     *
     * @param actorSystem the actorSystem in which the {@code MessageMapperExtension} should be loaded.
     * @param config the configuration for this extension.
     * @return the {@code MessageMapperExtension} implementation.
     * @throws NullPointerException if {@code actorSystem} is {@code null}.
     * @since 3.0.0
     */
    static MessageMapperExtension get(final ActorSystem actorSystem, final Config config) {
        checkNotNull(actorSystem, "actorSystem");
        checkNotNull(config, "config");
        final var extensionIdConfig = ExtensionId.computeConfig(config);
        return DittoExtensionIds.get(actorSystem)
                .computeIfAbsent(extensionIdConfig, ExtensionId::new)
                .get(actorSystem);
    }

    final class ExtensionId extends DittoExtensionPoint.ExtensionId<MessageMapperExtension> {

        private static final String CONFIG_KEY = "message-mapper-extension";

        private ExtensionId(final ExtensionIdConfig<MessageMapperExtension> extensionIdConfig) {
            super(extensionIdConfig);
        }

        static ExtensionIdConfig<MessageMapperExtension> computeConfig(final Config config) {
            return ExtensionIdConfig.of(MessageMapperExtension.class, config, CONFIG_KEY);
        }

        @Override
        protected String getConfigKey() {
            return CONFIG_KEY;
        }

    }

}
