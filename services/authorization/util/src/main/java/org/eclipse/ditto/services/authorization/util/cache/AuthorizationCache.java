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

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.eclipse.ditto.model.enforcers.AclEnforcer;
import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.services.authorization.util.cache.entry.Entry;
import org.eclipse.ditto.services.authorization.util.config.CacheConfigReader;
import org.eclipse.ditto.utils.jsr305.annotations.AllParametersAndReturnValuesAreNonnullByDefault;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;

import akka.actor.ActorRef;
import scala.NotImplementedError;

/**
 * Cache of enforcer objects for each
 */
@AllParametersAndReturnValuesAreNonnullByDefault
public final class AuthorizationCache {

    private final AsyncLoadingCache<ResourceKey, Entry<Enforcer>> enforcerCache;
    private final AsyncLoadingCache<ResourceKey, Entry<ResourceKey>> idCache;

    /**
     * Creates a cache from configuration.
     *
     * @param cacheConfigReader config reader for authorization cache.
     */
    public AuthorizationCache(final CacheConfigReader cacheConfigReader) {
        // TODO: compute entity region map in root actor
        final Supplier<Map<String, ActorRef>> entityRegionMapSupplier = () -> { throw new NotImplementedError(); };
        final Map<String, ActorRef> entityRegionMap = entityRegionMapSupplier.get();

        final Duration askTimeout = cacheConfigReader.getAskTimeout();

        final EnforcerCacheLoader enforcerCacheLoader = new EnforcerCacheLoader(askTimeout, entityRegionMap);
        enforcerCache = cacheConfigReader.getEnforcerCacheConfigReader().toCaffeine().buildAsync(enforcerCacheLoader);

        final IdCacheLoader idCacheLoader = new IdCacheLoader(askTimeout, entityRegionMap, this);
        idCache = cacheConfigReader.getIdCacheConfigReader().toCaffeine().buildAsync(idCacheLoader);

    }

    void updateAcl(final ResourceKey resourceKey, final long revision, final AccessControlList acl) {
        final Supplier<Entry<Enforcer>> entrySupplier = () -> Entry.of(revision, AclEnforcer.of(acl));
        // accept potential race condition to react to events quickly
        enforcerCache.get(resourceKey, k -> entrySupplier.get())
                .thenAccept(cachedEntry -> {
                    if (cachedEntry.getRevision() < revision) {
                        enforcerCache.put(resourceKey, CompletableFuture.completedFuture(entrySupplier.get()));
                    }
                });
    }

    // TODO: DO NOT save policy id relation into the ID cache because it is always the identity relation.
    // TODO: DO NOT save message commands into the ID cache to not waste memory and bandwidth
}
