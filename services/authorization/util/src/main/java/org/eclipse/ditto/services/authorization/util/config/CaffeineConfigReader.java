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

import org.eclipse.ditto.services.base.config.AbstractConfigReader;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.typesafe.config.Config;

/**
 * Configuration reader for id and enforcer cache.
 */
public final class CaffeineConfigReader extends AbstractConfigReader {

    private static final String PATH_MAXIMUM_SIZE = "maximum-size";
    private static final String PATH_EXPIRE_AFTER_WRITE = "expire-after-write";

    CaffeineConfigReader(final Config config) {
        super(config);
    }

    /**
     * Retrieve the maximum size of a cache.
     *
     * @return the maximum size if it exists.
     */
    public long getMaximumSize() {
        return config.getLong(PATH_MAXIMUM_SIZE);
    }

    /**
     * Retrieve duration after which a cache entry expires.
     *
     * @return duration between write and expiration.
     */
    public Duration getExpireAfterWrite() {
        return config.getDuration(PATH_EXPIRE_AFTER_WRITE);
    }

    /**
     * Converts this setting into a Caffeine cache builder.
     */
    public Caffeine<Object, Object> toCaffeine() {
        final Caffeine<Object, Object> caffeine = Caffeine.newBuilder();
        caffeine.maximumSize(getMaximumSize());
        caffeine.expireAfterWrite(getExpireAfterWrite());
        return caffeine;
    }
}
