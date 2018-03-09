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
import java.util.Optional;

import org.eclipse.ditto.services.base.AbstractConfigReader;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.typesafe.config.Config;

/**
 * Configuration reader for id and enforcer cache.
 */
public final class CaffeineConfigReader extends AbstractConfigReader {

    CaffeineConfigReader(final Config config) {
        super(config);
    }

    /**
     * Retrieve the maximum size of a cache.
     *
     * @return the maximum size if it exists.
     */
    public Optional<Long> getMaximumSize() {
        return getIfPresent("maximum-size", config::getLong);
    }

    /**
     * Retrieve duration after which a cache entry expires.
     *
     * @return duration between write and expiration.
     */
    public Optional<Duration> getExpireAfterWrite() {
        return getIfPresent("expire-after-write", config::getDuration);
    }

    /**
     * Converts this setting into a Caffeine cache builder.
     */
    public Caffeine<Object, Object> toCaffeine() {
        final Caffeine<Object, Object> caffeine = Caffeine.newBuilder();
        getMaximumSize().ifPresent(caffeine::maximumSize);
        getExpireAfterWrite().ifPresent(caffeine::expireAfterWrite);
        return caffeine;
    }
}
