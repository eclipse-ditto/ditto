/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.service.updater.actors;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import javax.annotation.Nullable;

import org.eclipse.ditto.internal.utils.extension.DittoExtensionIds;
import org.eclipse.ditto.internal.utils.extension.DittoExtensionPoint;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.Metadata;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;

/**
 * Search update observer to be loaded by reflection.
 * Can be used as an extension point to observe search updates.
 * Implementations MUST have a public constructor taking an actorSystem as argument.
 *
 * @since 2.3.0
 */
public interface SearchUpdateObserver extends DittoExtensionPoint {

    /**
     * Process the given {@code Metadata} and thing as {@code JsonObject}.
     *
     * @param metadata the metadata for the update.
     * @param thingJson the thing used for the update as jsonObject.
     */
    void process(final Metadata metadata, @Nullable final JsonObject thingJson);

    /**
     * Loads the implementation of {@code SearchUpdateObserver} which is configured for the
     * {@code ActorSystem}.
     *
     * @param actorSystem the actorSystem in which the {@code SearchUpdateObserver} should be loaded.
     * @param config the configuration for this extension.
     * @return the {@code SearchUpdateObserver} implementation.
     * @throws NullPointerException if {@code actorSystem} is {@code null}.
     */
    static SearchUpdateObserver get(final ActorSystem actorSystem, final Config config) {
        checkNotNull(actorSystem, "actorSystem");
        checkNotNull(config, "config");
        final var extensionIdConfig = ExtensionId.computeConfig(config);

        return DittoExtensionIds.get(actorSystem)
                .computeIfAbsent(extensionIdConfig, ExtensionId::new)
                .get(actorSystem);
    }

    final class ExtensionId extends DittoExtensionPoint.ExtensionId<SearchUpdateObserver> {

        private static final String CONFIG_KEY = "search-update-observer";

        private ExtensionId(final ExtensionIdConfig<SearchUpdateObserver> extensionIdConfig) {
            super(extensionIdConfig);
        }

        static ExtensionIdConfig<SearchUpdateObserver> computeConfig(final Config config) {
            return ExtensionIdConfig.of(SearchUpdateObserver.class, config, CONFIG_KEY);
        }

        @Override
        protected String getConfigKey() {
            return CONFIG_KEY;
        }

    }

}
