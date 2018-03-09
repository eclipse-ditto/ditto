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
package org.eclipse.ditto.services.authorization.util.cache;

import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.services.authorization.util.config.CacheConfigReader;
import org.eclipse.ditto.utils.jsr305.annotations.AllParametersAndReturnValuesAreNonnullByDefault;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;

/**
 * Cache of enforcer objects for each
 */
@AllParametersAndReturnValuesAreNonnullByDefault
public final class AuthorizationCache {

    private final AsyncLoadingCache<ResourceKey, Versioned<ResourceKey>> idCache;
    private final AsyncLoadingCache<ResourceKey, Versioned<Enforcer>> enforcerCache;

    /**
     * Creates a cache from configuration.
     *
     * @param cacheConfigReader config reader for authorization cache.
     */
    public AuthorizationCache(final CacheConfigReader cacheConfigReader) {
        final IdCacheLoader idCacheLoader = new IdCacheLoader();
        final EnforcerCacheLoader enforcerCacheLoader = new EnforcerCacheLoader();
        idCache = cacheConfigReader.getIdCacheConfigReader().toCaffeine().buildAsync(idCacheLoader);
        enforcerCache = cacheConfigReader.getEnforcerCacheConfigReader().toCaffeine().buildAsync(enforcerCacheLoader);
    }
}
