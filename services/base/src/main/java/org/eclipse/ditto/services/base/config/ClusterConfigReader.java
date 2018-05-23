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
package org.eclipse.ditto.services.base.config;

import java.time.Duration;

import com.typesafe.config.Config;

/**
 * Cluster majority check configurations.
 */
public final class ClusterConfigReader extends AbstractConfigReader {

    /**
     * Whether to enable majority check by default.
     */
    public static final boolean DEFAULT_MAJORITY_CHECK_ENABLED = false;

    /**
     * Number of cluster shards by default.
     */
    public static final int DEFAULT_NUMBER_OF_SHARDS = 30;

    /**
     * Delay of majority check by default.
     */
    public static final Duration DEFAULT_MAJORITY_CHECK_DELAY = Duration.ofSeconds(30L);

    private static final String PATH_INSTANCE_INDEX = "instance-index";
    private static final String PATH_NUMBER_OF_SHARDS = "number-of-shards";
    private static final String PATH_MAJORITY_CHECK = "majority-check";
    private static final String PATH_MAJORITY_CHECK_ENABLED = path(PATH_MAJORITY_CHECK, "enabled");
    private static final String PATH_MAJORITY_CHECK_DELAY = path(PATH_MAJORITY_CHECK, "delay");

    ClusterConfigReader(final Config config) {
        super(config);
    }

    /**
     * Retrieve the instance index of the cluster node.
     *
     * @return instance index.
     */
    public int instanceIndex() {
        return getIfPresent(PATH_INSTANCE_INDEX, config::getInt).orElse(-1);
    }

    /**
     * Retrieve the number of shards in a cluster.
     *
     * @return number of shards.
     */
    public int numberOfShards() {
        return getIfPresent(PATH_NUMBER_OF_SHARDS, config::getInt).orElse(DEFAULT_NUMBER_OF_SHARDS);
    }

    /**
     * Retrieve whether cluster majority check is enabled.
     *
     * @return whether cluster majority check is enabled.
     */
    public boolean majorityCheckEnabled() {
        return getIfPresent(PATH_MAJORITY_CHECK_ENABLED, config::getBoolean).orElse(DEFAULT_MAJORITY_CHECK_ENABLED);
    }

    /**
     * Get delay of cluster majority check.
     *
     * @return delay of cluster majority check.
     */
    public Duration majorityCheckDelay() {
        return getIfPresent(PATH_MAJORITY_CHECK_DELAY, config::getDuration).orElse(DEFAULT_MAJORITY_CHECK_DELAY);
    }

}
