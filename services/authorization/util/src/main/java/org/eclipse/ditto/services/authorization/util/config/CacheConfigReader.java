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
package org.eclipse.ditto.services.authorization.util.config;

import java.time.Duration;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.base.config.AbstractConfigReader;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;

import com.typesafe.config.Config;

/**
 * Configuration reader for authorization cache.
 */
@Immutable
@AllValuesAreNonnullByDefault
public final class CacheConfigReader extends AbstractConfigReader {

    private static final Duration DEFAULT_ASK_TIMEOUT = Duration.ofSeconds(10);

    CacheConfigReader(final Config config) {
        super(config);
    }

    /**
     * Retrieve duration to wait for entity shard regions.
     *
     * @return Internal ask timeout duration.
     */
    public Duration getAskTimeout() {
        return getIfPresent("ask-timeout", config::getDuration).orElse(DEFAULT_ASK_TIMEOUT);
    }

    /**
     * Retrieve config reader for the id cache.
     *
     * @return the config reader.
     */
    public CaffeineConfigReader getIdCacheConfigReader() {
        return getCaffeineConfigReader("id");
    }

    /**
     * Retrieve config reader for the enforcer cache.
     *
     * @return the config reader.
     */
    public CaffeineConfigReader getEnforcerCacheConfigReader() {
        return getCaffeineConfigReader("enforcer");
    }

    private CaffeineConfigReader getCaffeineConfigReader(final String childPath) {
        return new CaffeineConfigReader(getChild(childPath));
    }

}
