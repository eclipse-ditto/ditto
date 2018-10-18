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

import java.time.Duration;
import java.util.Collections;

import org.eclipse.ditto.services.utils.config.AbstractConfigReader;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSystem;
import akka.cluster.ddata.ReplicatorSettings;

/**
 * Distributed data configuration reader.
 */
public final class DDataConfigReader extends AbstractConfigReader {

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

    private DDataConfigReader(final Config config) {
        super(config);
    }

    /**
     * Create a distributed data configuration reader.
     */
    public static DDataConfigReader of(final ActorSystem system) {
        return new DDataConfigReader(system.settings().config().getConfig(FALLBACK_CONFIG_PATH));
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
     * @return name of the replicator.
     */
    public String name() {
        return config.getString(ACTOR_NAME_KEY);
    }

    /**
     * Set the name of the replicator.
     *
     * @param name the new name of the replicator.
     * @return a copy of this object with a new name of the replicator.
     */
    public DDataConfigReader withName(final String name) {
        return with(ACTOR_NAME_KEY, name);
    }

    /**
     * @return cluster role of members with replicas of the the distributed collection.
     */
    public String role() {
        return config.getString(CLUSTER_ROLE_KEY);
    }

    /**
     * Set the cluster role of members with replicas of the distributed collection..
     *
     * @param role the new cluster role of members with replicas of the distributed collection..
     * @return a copy of this object with a new cluster role of members with replicas of the distributed collection..
     */
    public DDataConfigReader withRole(final String role) {
        return with(CLUSTER_ROLE_KEY, role);
    }

    /**
     * @return timeout of reads.
     */
    public Duration readTimeout() {
        return getIfPresent(READ_TIMEOUT_KEY, config::getDuration).orElse(DEFAULT_ASK_TIMEOUT);
    }

    /**
     * Set the timeout of GET-messages to the replicator.
     *
     * @param askTimeout the new timeout of messages to the replicator.
     * @return a copy of this object with a new timeout of messages to the replicator.
     */
    public DDataConfigReader withReadTimeout(final Duration askTimeout) {
        return with(READ_TIMEOUT_KEY, askTimeout);
    }

    /**
     * @return timeout of writes.
     */
    public Duration writeTimeout() {
        return getIfPresent(WRITE_TIMEOUT_KEY, config::getDuration).orElse(DEFAULT_ASK_TIMEOUT);
    }

    /**
     * Set the timeout of UPDATE-messages to the replicator.
     *
     * @param askTimeout the new timeout of messages to the replicator.
     * @return a copy of this object with a new timeout of messages to the replicator.
     */
    public DDataConfigReader withWriteTimeout(final Duration askTimeout) {
        return with(WRITE_TIMEOUT_KEY, askTimeout);
    }

    private DDataConfigReader with(final String configKey, final Object configValue) {
        final Config newConfig = ConfigFactory.parseMap(Collections.singletonMap(configKey, configValue))
                .withFallback(config);
        return new DDataConfigReader(newConfig);
    }
}
