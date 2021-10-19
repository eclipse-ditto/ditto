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
package org.eclipse.ditto.concierge.service.common;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.cache.config.CacheConfig;
import org.eclipse.ditto.internal.utils.cacheloaders.config.AskWithRetryConfig;

/**
 * Provides configuration settings of the caches of Concierge.
 */
@Immutable
public interface CachesConfig {

    /**
     * Returns the configuration for the used "ask with retry" pattern in the concierge caches to load things+policies.
     *
     * @return the "ask with retry" pattern config for retrieval of things and policies.
     */
    AskWithRetryConfig getAskWithRetryConfig();

    /**
     * Returns the config of the ID cache.
     *
     * @return the config.
     */
    CacheConfig getIdCacheConfig();

    /**
     * Returns the config of the policy cache.
     *
     * @return the config.
     */
    CacheConfig getPolicyCacheConfig();

    /**
     * Returns the config of the enforcer cache.
     *
     * @return the config.
     */
    CacheConfig getEnforcerCacheConfig();

}
