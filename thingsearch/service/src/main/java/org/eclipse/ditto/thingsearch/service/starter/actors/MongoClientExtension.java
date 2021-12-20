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
package org.eclipse.ditto.thingsearch.service.starter.actors;

import javax.annotation.Nullable;

import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.DittoMongoClient;
import org.eclipse.ditto.internal.utils.persistence.mongo.MongoClientWrapper;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.MongoDbConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.monitoring.KamonCommandListener;
import org.eclipse.ditto.internal.utils.persistence.mongo.monitoring.KamonConnectionPoolListener;
import org.eclipse.ditto.thingsearch.service.common.config.DittoSearchConfig;

import com.mongodb.event.CommandListener;
import com.mongodb.event.ConnectionPoolListener;

import akka.actor.AbstractExtensionId;
import akka.actor.ActorSystem;
import akka.actor.ExtendedActorSystem;
import akka.actor.Extension;

/**
 * Actor system extension to share a MongoDB client between actors.
 */
public final class MongoClientExtension implements Extension {

    private static final String SEARCH_PREFIX = "search";
    private static final String UPDATER_PREFIX = "updater";
    private static final Id EXTENSION_ID = new Id();

    private final DittoMongoClient mongoClient;
    private final DittoMongoClient updaterClient;

    private MongoClientExtension(final DittoMongoClient mongoClient,
            final DittoMongoClient updaterClient) {

        this.mongoClient = mongoClient;
        this.updaterClient = updaterClient;
    }

    /**
     * Get the extension.
     *
     * @param system the actor system.
     * @return this extension.
     */
    public static MongoClientExtension get(final ActorSystem system) {
        return EXTENSION_ID.get(system);
    }

    /**
     * Return the MongoDB client with Kamon metric prefix "search".
     *
     * @return the client.
     */
    public DittoMongoClient getSearchClient() {
        return mongoClient;
    }

    /**
     * Return the MongoDB client with Kamon metric prefix "updater".
     *
     * @return the client.
     */
    public DittoMongoClient getUpdaterClient() {
        return updaterClient;
    }

    private static final class Id extends AbstractExtensionId<MongoClientExtension> {

        @Override
        public MongoClientExtension createExtension(final ExtendedActorSystem system) {
            final var dittoConfig = DefaultScopedConfig.dittoScoped(system.settings().config());
            final var searchConfig = DittoSearchConfig.of(dittoConfig);
            final var mongoDbConfig = searchConfig.getMongoDbConfig();
            final var monitoringConfig = mongoDbConfig.getMonitoringConfig();
            final var searchClient = MongoClientWrapper.getBuilder(mongoDbConfig)
                    .addCommandListener(getCommandListenerOrNull(monitoringConfig, SEARCH_PREFIX))
                    .addConnectionPoolListener(getConnectionPoolListenerOrNull(monitoringConfig, SEARCH_PREFIX))
                    .build();
            final var updaterClient = MongoClientWrapper.getBuilder(mongoDbConfig)
                    .addCommandListener(getCommandListenerOrNull(monitoringConfig, UPDATER_PREFIX))
                    .addConnectionPoolListener(getConnectionPoolListenerOrNull(monitoringConfig, UPDATER_PREFIX))
                    .build();
            return new MongoClientExtension(searchClient, updaterClient);
        }

        @Nullable
        private static CommandListener getCommandListenerOrNull(final MongoDbConfig.MonitoringConfig monitoringConfig,
                final String prefix) {

            return monitoringConfig.isCommandsEnabled() ? new KamonCommandListener(prefix) : null;
        }

        @Nullable
        private static ConnectionPoolListener getConnectionPoolListenerOrNull(
                final MongoDbConfig.MonitoringConfig monitoringConfig,
                final String prefix
        ) {
            return monitoringConfig.isConnectionPoolEnabled() ? new KamonConnectionPoolListener(prefix) : null;
        }

    }

}
