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
package org.eclipse.ditto.services.base.metrics;

import java.util.AbstractMap;
import java.util.Map;

import com.codahale.metrics.MetricRegistry;
import com.typesafe.config.Config;

import akka.actor.ActorSystem;
import akka.contrib.persistence.mongodb.MongoPersistenceExtension;
import akka.contrib.persistence.mongodb.MongoPersistenceExtension$;

/**
 * Factory for a accessing the {@link MetricRegistry} provided by the {@link MongoPersistenceExtension}.
 */
public final class MongoDbMetricRegistryFactory {

    private static final String REGISTRY_NAME = "mongodb";

    private MongoDbMetricRegistryFactory() {
        throw new AssertionError();
    }

    /**
     * Gets the MongoDB-MetricRegistry, creates it if it does not exist yet.
     *
     * @param actorSystem the ActorSystem
     * @param config the Config to use
     */
    @SuppressWarnings("RedundantCast")
    public static Map.Entry<String, MetricRegistry> createOrGet(final ActorSystem actorSystem, final Config config) {
        // Would not compile without cast!
        // The cast is not redundant for Maven.
        final MetricRegistry registry =
                ((MongoPersistenceExtension) MongoPersistenceExtension$.MODULE$.apply(actorSystem))
                        .configured(config)
                        .registry();
        return new AbstractMap.SimpleImmutableEntry<>(REGISTRY_NAME, registry);
    }

}
