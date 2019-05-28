/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.utils.cache.config;

import java.time.Duration;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.AbstractConfigReader;

import com.typesafe.config.Config;

/**
 * Configuration reader for authorization cache.
 */
@Immutable
public final class CachesConfigReader extends AbstractConfigReader {

    private static final Duration DEFAULT_ASK_TIMEOUT = Duration.ofSeconds(10);

    /**
     * Constructs a new CachesConfigReader based on the passed {@code config}.
     *
     * @param config the config to build this CachesConfigReader with.
     */
    public CachesConfigReader(final Config config) {
        super(config);
    }

    /**
     * Retrieve duration to wait for entity shard regions.
     *
     * @return Internal ask timeout duration.
     */
    public Duration askTimeout() {
        return getIfPresent("ask-timeout", config::getDuration).orElse(DEFAULT_ASK_TIMEOUT);
    }

    /**
     * Retrieve config reader for the id cache.
     *
     * @return the config reader.
     */
    public CacheConfigReader id() {
        return getCacheConfigReader("id");
    }

    /**
     * Retrieve config reader for the enforcer cache.
     *
     * @return the config reader.
     */
    public CacheConfigReader enforcer() {
        return getCacheConfigReader("enforcer");
    }

    private CacheConfigReader getCacheConfigReader(final String childPath) {
        return CacheConfigReader.newInstance(getChild(childPath));
    }

}
