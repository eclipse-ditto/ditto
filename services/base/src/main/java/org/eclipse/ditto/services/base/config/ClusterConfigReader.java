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
package org.eclipse.ditto.services.base.config;

import org.eclipse.ditto.services.utils.config.AbstractConfigReader;

import com.typesafe.config.Config;

/**
 * Cluster majority check configurations.
 */
public final class ClusterConfigReader extends AbstractConfigReader {

    /**
     * Number of cluster shards by default.
     */
    public static final int DEFAULT_NUMBER_OF_SHARDS = 30;

    private static final String PATH_NUMBER_OF_SHARDS = "number-of-shards";

    ClusterConfigReader(final Config config) {
        super(config);
    }

    /**
     * Retrieve the number of shards in a cluster.
     *
     * @return number of shards.
     */
    public int numberOfShards() {
        return getIfPresent(PATH_NUMBER_OF_SHARDS, config::getInt).orElse(DEFAULT_NUMBER_OF_SHARDS);
    }

}
