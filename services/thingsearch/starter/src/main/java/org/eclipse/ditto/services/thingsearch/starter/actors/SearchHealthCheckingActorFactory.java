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
package org.eclipse.ditto.services.thingsearch.starter.actors;

import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.ditto.services.thingsearch.common.config.SearchConfig;
import org.eclipse.ditto.services.thingsearch.common.config.UpdaterConfig;
import org.eclipse.ditto.services.utils.akka.streaming.TimestampPersistence;
import org.eclipse.ditto.services.utils.health.AbstractHealthCheckingActor;
import org.eclipse.ditto.services.utils.health.CompositeCachingHealthCheckingActor;
import org.eclipse.ditto.services.utils.health.config.HealthCheckConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.MongoHealthChecker;

import akka.actor.Props;

/**
 * Provides an actor for checking and caching the health of the search service.
 */
final class SearchHealthCheckingActorFactory {

    /**
     * The required name of the Actor to be created in the ActorSystem.
     */
    public static final String ACTOR_NAME = AbstractHealthCheckingActor.ACTOR_NAME;

    private static final String PERSISTENCE_LABEL = "persistence";
    private static final String THINGS_SYNC_LABEL = "thingsSync";
    private static final String POLICIES_SYNC_LABEL = "policiesSync";

    private SearchHealthCheckingActorFactory() {
        super();
    }

    /**
     * Creates Akka configuration object Props for a health checking actor.
     *
     * @param searchConfig the configuration settings.
     * @param thingsSyncPersistence the things sync persistence to determine time of last successful things-sync.
     * @param policiesSyncPersistence the policies sync persistence to determine time of last successful policies-sync.
     * @return the Akka configuration Props object.
     */
    public static Props props(final SearchConfig searchConfig, final TimestampPersistence thingsSyncPersistence,
            final TimestampPersistence policiesSyncPersistence) {

        final Map<String, Props> childActorProps = new LinkedHashMap<>();

        final HealthCheckConfig healthCheckConfig = searchConfig.getHealthCheckConfig();
        final boolean healthCheckEnabled = healthCheckConfig.isEnabled();
        if (healthCheckEnabled) {
            childActorProps.put(PERSISTENCE_LABEL, MongoHealthChecker.props(searchConfig.getMongoDbConfig()));
        }

        final UpdaterConfig updaterConfig = searchConfig.getUpdaterConfig();
        childActorProps.put(THINGS_SYNC_LABEL,
                LastSuccessfulStreamCheckingActor.props(updaterConfig.getThingsSyncConfig(), thingsSyncPersistence));
        childActorProps.put(POLICIES_SYNC_LABEL,
                LastSuccessfulStreamCheckingActor.props(updaterConfig.getPoliciesSyncConfig(),
                        policiesSyncPersistence));

        return CompositeCachingHealthCheckingActor.props(childActorProps, healthCheckConfig.getInterval(),
                healthCheckEnabled);
    }

}
