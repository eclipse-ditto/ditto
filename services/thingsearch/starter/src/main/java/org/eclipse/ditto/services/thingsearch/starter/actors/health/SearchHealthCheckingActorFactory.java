/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.thingsearch.starter.actors.health;

import java.time.Duration;
import java.util.LinkedHashMap;

import org.eclipse.ditto.services.thingsearch.common.util.ConfigKeys;
import org.eclipse.ditto.services.utils.akka.streaming.StreamMetadataPersistence;
import org.eclipse.ditto.services.utils.health.AbstractHealthCheckingActor;
import org.eclipse.ditto.services.utils.health.CompositeCachingHealthCheckingActor;
import org.eclipse.ditto.services.utils.health.PersistenceHealthCheckingActor;

import com.typesafe.config.Config;

import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * Provides an actor for checking and caching the health of the search service.
 */
public class SearchHealthCheckingActorFactory {

    /**
     * The required name of the Actor to be created in the ActorSystem.
     */
    public static final String ACTOR_NAME = AbstractHealthCheckingActor.ACTOR_NAME;

    private static final String PERSISTENCE_LABEL = "persistence";

    private static final String THINGS_SYNC_LABEL = "thingsSync";

    private static final String POLICIES_SYNC_LABEL = "policiesSync";

    private SearchHealthCheckingActorFactory() {}

    /**
     * Creates Akka configuration object Props for a health checking actor.
     *
     * @param config the configuration settings.
     * @param mongoClientActor the actor handling mongodb calls.
     * @param thingsSyncPersistence the things sync persistence to determine time of last successful things-sync.
     * @param policiesSyncPersistence the policies sync persistence to determine time of last successful policies-sync.
     * @return the Akka configuration Props object.
     */
    public static Props props(final Config config, final ActorRef mongoClientActor,
            final StreamMetadataPersistence thingsSyncPersistence,
            final StreamMetadataPersistence policiesSyncPersistence) {

        final LinkedHashMap<String, Props> childActorProps = new LinkedHashMap<>();

        final boolean enablePersistenceCheck = config.getBoolean(ConfigKeys.HEALTH_CHECK_PERSISTENCE_ENABLED);
        if (enablePersistenceCheck) {
            childActorProps.put(PERSISTENCE_LABEL, PersistenceHealthCheckingActor.props(mongoClientActor));
        }

        childActorProps.put(THINGS_SYNC_LABEL,
                LastSuccessfulStreamCheckingActor.props(
                        LastSuccessfulStreamCheckingActorConfigurationProperties.thingsSync(config,
                                thingsSyncPersistence)));

        childActorProps.put(POLICIES_SYNC_LABEL,
                LastSuccessfulStreamCheckingActor.props(
                        LastSuccessfulStreamCheckingActorConfigurationProperties.policiesSync(config,
                                policiesSyncPersistence)));

        final boolean healthCheckEnabled = config.getBoolean(ConfigKeys.HEALTH_CHECK_ENABLED);
        final Duration healthCheckInterval = config.getDuration(ConfigKeys.HEALTH_CHECK_INTERVAL);
        return CompositeCachingHealthCheckingActor.props(childActorProps, healthCheckInterval, healthCheckEnabled);
    }
}
