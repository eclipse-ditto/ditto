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
package org.eclipse.ditto.services.concierge.util.config;

import java.time.Duration;

import javax.annotation.concurrent.Immutable;

/**
 * Provides configuration settings of a particular cache of Concierge.
 */
@Immutable
public interface CacheConfig {

    /**
     * Returns the maximum size of a cache.
     *
     * @return the maximum size.
     */
    long getMaximumSize();

    /**
     * Returns duration after which a cache entry expires.
     *
     * @return the duration between write and expiration.
     */
    Duration getExpireAfterWrite();

}
