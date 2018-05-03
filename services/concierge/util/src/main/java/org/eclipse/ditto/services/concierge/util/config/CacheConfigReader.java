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
package org.eclipse.ditto.services.concierge.util.config;

import java.time.Duration;

import org.eclipse.ditto.services.base.config.AbstractConfigReader;

import com.typesafe.config.Config;

/**
 * Configuration reader for id and enforcer cache.
 */
public final class CacheConfigReader extends AbstractConfigReader {

    private static final String PATH_MAXIMUM_SIZE = "maximum-size";
    private static final String PATH_EXPIRE_AFTER_WRITE = "expire-after-write";

    CacheConfigReader(final Config config) {
        super(config);
    }

    /**
     * Retrieve the maximum size of a cache.
     *
     * @return the maximum size if it exists.
     */
    public long maximumSize() {
        return config.getLong(PATH_MAXIMUM_SIZE);
    }

    /**
     * Retrieve duration after which a cache entry expires.
     *
     * @return duration between write and expiration.
     */
    public Duration expireAfterWrite() {
        return config.getDuration(PATH_EXPIRE_AFTER_WRITE);
    }
}
