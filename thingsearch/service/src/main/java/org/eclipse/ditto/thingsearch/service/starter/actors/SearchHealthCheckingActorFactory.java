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
package org.eclipse.ditto.thingsearch.service.starter.actors;

import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.ditto.thingsearch.service.common.config.SearchConfig;
import org.eclipse.ditto.thingsearch.service.updater.actors.SearchUpdaterRootActor;
import org.eclipse.ditto.internal.utils.health.AbstractHealthCheckingActor;
import org.eclipse.ditto.internal.utils.health.CompositeCachingHealthCheckingActor;
import org.eclipse.ditto.internal.utils.health.SingletonStatusReporter;
import org.eclipse.ditto.internal.utils.health.config.HealthCheckConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.MongoHealthChecker;

import akka.actor.ActorRef;
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
    private static final String BACKGROUND_SYNC_LABEL = "backgroundSync";

    private SearchHealthCheckingActorFactory() {
        throw new AssertionError();
    }

    /**
     * Creates Akka configuration object Props for a health checking actor.
     *
     * @param searchConfig the configuration settings.
     * @param backgroundSyncActorProxy proxy actor for RetrieveHealth messages to the background sync actor.
     * @return the Akka configuration Props object.
     */
    public static Props props(final SearchConfig searchConfig, final ActorRef backgroundSyncActorProxy) {

        final Map<String, Props> childActorProps = new LinkedHashMap<>();

        final HealthCheckConfig healthCheckConfig = searchConfig.getHealthCheckConfig();
        final boolean healthCheckEnabled = healthCheckConfig.isEnabled();
        if (healthCheckEnabled) {
            childActorProps.put(PERSISTENCE_LABEL, MongoHealthChecker.props());
        }

        childActorProps.put(BACKGROUND_SYNC_LABEL,
                SingletonStatusReporter.props(SearchUpdaterRootActor.CLUSTER_ROLE, backgroundSyncActorProxy));

        return CompositeCachingHealthCheckingActor.props(childActorProps, healthCheckConfig.getInterval(),
                healthCheckEnabled);
    }

}
