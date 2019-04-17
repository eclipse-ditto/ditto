/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.concierge.util.config;

import java.time.Duration;

import org.eclipse.ditto.services.utils.config.AbstractConfigReader;

import com.typesafe.config.Config;

/**
 * Configuration reader for id and enforcer cache.
 */
public final class CacheConfigReader extends AbstractConfigReader {

    private static final String PATH_MAXIMUM_SIZE = "maximum-size";
    private static final String PATH_EXPIRE_AFTER_WRITE = "expire-after-write";
    private static final String PATH_EXPIRE_AFTER_ACCESS = "expire-after-access";

    private CacheConfigReader(final Config config) {
        super(config);
    }

    public static CacheConfigReader newInstance(final Config config) {
        return new CacheConfigReader(config);
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
     * Retrieve duration after which a written cache entry expires.
     *
     * @return duration between write and expiration.
     */
    public Duration expireAfterWrite() {
        return config.getDuration(PATH_EXPIRE_AFTER_WRITE);
    }

    /**
     * Retrieve duration after which an accessed cache entry expires.
     *
     * @return duration between last access and expiration.
     */
    public Duration expireAfterAccess() {
        return config.getDuration(PATH_EXPIRE_AFTER_ACCESS);
    }

}
