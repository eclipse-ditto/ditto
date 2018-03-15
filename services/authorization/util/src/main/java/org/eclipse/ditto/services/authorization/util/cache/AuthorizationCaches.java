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
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import org.eclipse.ditto.model.enforcers.AclEnforcer;
import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.services.authorization.util.EntityRegionMap;
import org.eclipse.ditto.services.authorization.util.cache.entry.Entry;
import org.eclipse.ditto.services.authorization.util.config.CacheConfigReader;
import org.eclipse.ditto.services.authorization.util.config.CachesConfigReader;
import org.eclipse.ditto.signals.commands.policies.PolicyCommand;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Caches of entity IDs and enforcer objects.
 */
public final class AuthorizationCaches {

    private final AsyncLoadingCache<ResourceKey, Entry<Enforcer>> enforcerCache;
    private final AsyncLoadingCache<ResourceKey, Entry<ResourceKey>> idCache;

    /**
     * Creates a cache from configuration.
     *
     * @param cachesConfigReader config reader for authorization cache.
     * @param entityRegionMap map resource types to the shard regions of corresponding entities.
     */
    public AuthorizationCaches(final CachesConfigReader cachesConfigReader, final EntityRegionMap entityRegionMap) {

        final Duration askTimeout = cachesConfigReader.askTimeout();

        final EnforcerCacheLoader enforcerCacheLoader = new EnforcerCacheLoader(askTimeout, entityRegionMap);
        enforcerCache = caffeine(cachesConfigReader.enforcer()).buildAsync(enforcerCacheLoader);

        final IdCacheLoader idCacheLoader = new IdCacheLoader(askTimeout, entityRegionMap, this);
        idCache = caffeine(cachesConfigReader.id()).buildAsync(idCacheLoader);

    }

    /**
     * By an entity cache key, look up the enforcer cache key and the enforcer itself.
     *
     * @param entityKey cache key of an entity.
     * @param consumer handler of cache lookup results.
     */
    public void retrieve(final ResourceKey entityKey, final BiConsumer<Entry<ResourceKey>, Entry<Enforcer>> consumer) {
        if (Objects.equals(PolicyCommand.RESOURCE_TYPE, entityKey.getResourceType())) {
            // Enforcer cache key of a policy is always identical to the entity cache key of the policy.
            // No need to save the identity relation in entity cache and waste memory and bandwidth.
            enforcerCache.get(entityKey)
                    .thenAccept(enforcerEntry -> consumer.accept(Entry.permanent(entityKey), enforcerEntry));
        } else {
            idCache.get(entityKey).thenAccept(enforcerKeyEntry -> {
                if (enforcerKeyEntry.exists()) {
                    enforcerCache.get(enforcerKeyEntry.getValue())
                            .thenAccept(enforcerEntry -> consumer.accept(enforcerKeyEntry, enforcerEntry));
                } else {
                    consumer.accept(enforcerKeyEntry, Entry.nonexistent());
                }
            });
        }
    }

    /**
     * Invalidate an entry in the entity ID cache.
     *
     * @param resourceKey cache key of the entity.
     */
    public void invalidateEntityId(final ResourceKey resourceKey) {
        idCache.synchronous().invalidate(resourceKey);
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

    private Caffeine<Object, Object> caffeine(final CacheConfigReader cacheConfigReader) {
        final Caffeine<Object, Object> caffeine = Caffeine.newBuilder();
        caffeine.maximumSize(cacheConfigReader.maximumSize());
        caffeine.expireAfterWrite(cacheConfigReader.expireAfterWrite());
        return caffeine;
    }
}
