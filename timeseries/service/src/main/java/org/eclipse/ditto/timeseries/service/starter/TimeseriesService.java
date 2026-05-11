/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.timeseries.service.starter;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Props;
import org.eclipse.ditto.base.service.DittoService;
import org.eclipse.ditto.base.service.config.DittoServiceConfig;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.DefaultMongoDbConfig;
import org.eclipse.ditto.timeseries.api.TimeseriesMessagingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigValueFactory;

/**
 * Entry point of the Timeseries service.
 */
public final class TimeseriesService extends DittoService<DittoServiceConfig> {

    /**
     * Name of the Timeseries service (used for {@code ditto.service-name} HOCON and logging).
     */
    public static final String SERVICE_NAME = TimeseriesMessagingConstants.SERVICE_NAME;

    private static final Logger LOGGER = LoggerFactory.getLogger(TimeseriesService.class);

    private TimeseriesService() {
        super(LOGGER, SERVICE_NAME, TimeseriesRootActor.ACTOR_NAME);
    }

    /**
     * Starts the Timeseries service.
     *
     * @param args command-line arguments (currently ignored).
     */
    public static void main(final String[] args) {
        final TimeseriesService timeseriesService = new TimeseriesService();
        timeseriesService.start();
    }

    @Override
    protected DittoServiceConfig getServiceSpecificConfig(final ScopedConfig dittoConfig) {
        return DittoServiceConfig.of(dittoConfig, SERVICE_NAME);
    }

    @Override
    protected Props getMainRootActorProps(final DittoServiceConfig timeseriesConfig,
            final ActorRef pubSubMediator) {

        return TimeseriesRootActor.props(timeseriesConfig, pubSubMediator);
    }

    @Override
    protected Config appendPekkoPersistenceMongoUriToRawConfig() {
        // Mirror the things/policies/connectivity wiring: read ditto.mongodb.uri (resolved
        // from MONGO_DB_URI / MONGO_DB_DATABASE / ditto.mongodb.database) and inject it as
        // pekko.contrib.persistence.mongodb.mongo.mongouri so the journal + snapshot store
        // land in the same service-named database. Without this, the plugin falls back to
        // its reference.conf default db = "pekko-persistence", which (a) hides the data in
        // an unexpected DB and (b) splits journal vs snapshots if other components target
        // ditto.mongodb separately.
        //
        // serviceSpecificConfig is scoped to ditto.timeseries — too narrow; ditto.mongodb
        // sits one level up. things-service can use serviceSpecificConfig directly because
        // its ThingsConfig type exposes getMongoDbConfig(); our shared DittoServiceConfig
        // does not, so we re-scope rawConfig to ditto and pass that to DefaultMongoDbConfig.
        final var dittoScopedConfig = DefaultScopedConfig.dittoScoped(rawConfig);
        final var mongoDbConfig = DefaultMongoDbConfig.of(dittoScopedConfig);
        final String mongoDbUri = mongoDbConfig.getMongoDbUri();
        return rawConfig.withValue(MONGO_URI_CONFIG_PATH, ConfigValueFactory.fromAnyRef(mongoDbUri));
    }
}
