/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.utils.ddata;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.concurrent.Immutable;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSystem;
import akka.cluster.ddata.ReplicatorSettings;

/**
 * Distributed data configuration reader.
 */
@Immutable
public final class DistributedDataConfigReader {

    /**
     * Config key of reference config for Akka distributed data.
     */
    private static final String FALLBACK_CONFIG_PATH = "akka.cluster.distributed-data";

    private static final String ACTOR_NAME_KEY = "name";

    private static final String CLUSTER_ROLE_KEY = "role";

    /**
     * Config key prefix of options specific to Ditto's ReplicatorFacade.
     */
    private static final String DITTO_CONFIG_PREFIX = "ditto-replicator-facade.";

    private static final String READ_TIMEOUT_KEY = DITTO_CONFIG_PREFIX + "read-timeout";

    private static final String WRITE_TIMEOUT_KEY = DITTO_CONFIG_PREFIX + "write-timeout";

    private static final Duration DEFAULT_ASK_TIMEOUT = Duration.ofSeconds(5L);

    private final Config config;

    private DistributedDataConfigReader(final Config config) {
        this.config = config;
    }

    /**
     * Create a distributed data configuration reader with Akka's default options.
     *
     * @param actorSystem the actor actorSystem with the default distributed data configuration.
     * @param name the name of the replicator.
     * @param role the cluster role of members with replicas of the distributed collection.
     * @return a new config reader object.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static DistributedDataConfigReader of(final ActorSystem actorSystem, final CharSequence name,
            final CharSequence role) {

        final Map<String, Object> specificConfig = new HashMap<>(2);
        specificConfig.put(ACTOR_NAME_KEY, checkNotNull(name, "name").toString());
        specificConfig.put(CLUSTER_ROLE_KEY, checkNotNull(role, "role").toString());

        return new DistributedDataConfigReader(ConfigFactory.parseMap(specificConfig)
                .withFallback(getFallbackConfig(checkNotNull(actorSystem, "ActorSystem"))));
    }

    private static Config getFallbackConfig(final ActorSystem actorSystem) {
        final ActorSystem.Settings settings = actorSystem.settings();
        final Config config = settings.config();

        return config.getConfig(FALLBACK_CONFIG_PATH);
    }

    /**
     * Convert this object into replicator settings.
     *
     * @return replicator settings.
     */
    public ReplicatorSettings toReplicatorSettings() {
        return ReplicatorSettings.apply(config);
    }

    /**
     * Returns the name of the replicator.
     *
     * @return the name.
     */
    public String getName() {
        return config.getString(ACTOR_NAME_KEY);
    }

    /**
     * Returns the cluster role of members with replicas of the distributed collection.
     *
     * @return cluster role.
     */
    public String getRole() {
        return config.getString(CLUSTER_ROLE_KEY);
    }

    /**
     * Returns the timeout of GET-messages of the replicator.
     *
     * @return the timeout of reads.
     */
    public Duration getReadTimeout() {
        final String readTimeoutKey = READ_TIMEOUT_KEY;
        if (config.hasPath(readTimeoutKey)) {
            return config.getDuration(readTimeoutKey);
        }
        return DEFAULT_ASK_TIMEOUT;
    }

    /**
     * Returns the timeout of UPDATE-messages of the replicator.
     *
     * @return the timeout of writes.
     */
    public Duration getWriteTimeout() {
        final String writeTimeoutKey = WRITE_TIMEOUT_KEY;
        if (config.hasPath(writeTimeoutKey)) {
            return config.getDuration(writeTimeoutKey);
        }
        return DEFAULT_ASK_TIMEOUT;
    }

}
