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
package org.eclipse.ditto.thingsearch.service.persistence.write.streaming;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.javadsl.Source;
import org.eclipse.ditto.internal.utils.extension.DittoExtensionIds;
import org.eclipse.ditto.internal.utils.extension.DittoExtensionPoint;
import org.eclipse.ditto.thingsearch.persistence.api.model.AbstractWriteModel;

import com.typesafe.config.Config;

/**
 * Search Update Mapper to be loaded by reflection.
 * Can be used as an extension point to use custom map search updates.
 * Implementations MUST have a public constructor taking an actorSystem as argument.
 * <p>
 * The mapper operates on backend-neutral write models only. The Mongo incremental-diff computation that used
 * to live here (and its weak-ack-on-empty-diff behavior) moved into the per-backend write-execution seam
 * ({@code MongoSearchUpdaterFlow}), so this extension point no longer references any storage driver types.
 *
 * @since 2.1.0
 */
public abstract class SearchUpdateMapper implements DittoExtensionPoint {

    protected final ActorSystem actorSystem;

    protected SearchUpdateMapper(final ActorSystem actorSystem, final Config config) {
        this.actorSystem = actorSystem;
    }

    /**
     * Gets a backend-neutral write model of the search update and processes it.
     *
     * @param writeModel the current neutral write model.
     * @param lastWriteModel the previously-applied neutral write model.
     * @return the processed neutral write model(s), or an empty source to skip the update.
     */
    public abstract Source<AbstractWriteModel, NotUsed> processWriteModel(AbstractWriteModel writeModel,
            final AbstractWriteModel lastWriteModel);

    /**
     * Load a {@code SearchUpdateListener} dynamically according to the search configuration.
     *
     * @param actorSystem The actor system in which to load the listener.
     * @param config the configuration for this extension.
     * @return The listener.
     */
    public static SearchUpdateMapper get(final ActorSystem actorSystem, final Config config) {
        checkNotNull(actorSystem, "actorSystem");
        checkNotNull(config, "config");
        final var extensionIdConfig = ExtensionId.computeConfig(config);
        return DittoExtensionIds.get(actorSystem)
                .computeIfAbsent(extensionIdConfig, ExtensionId::new)
                .get(actorSystem);
    }

    /**
     * ID of the actor system extension to validate the {@code SearchUpdateListener}.
     */
    private static final class ExtensionId extends DittoExtensionPoint.ExtensionId<SearchUpdateMapper> {

        private static final String CONFIG_KEY = "search-update-mapper";

        private ExtensionId(final ExtensionIdConfig<SearchUpdateMapper> extensionIdConfig) {
            super(extensionIdConfig);
        }

        static ExtensionIdConfig<SearchUpdateMapper> computeConfig(final Config config) {
            return ExtensionIdConfig.of(SearchUpdateMapper.class, config, CONFIG_KEY);
        }

        @Override
        protected String getConfigKey() {
            return CONFIG_KEY;
        }

    }

}
