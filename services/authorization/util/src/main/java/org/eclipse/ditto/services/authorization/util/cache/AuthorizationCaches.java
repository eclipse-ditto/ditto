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
import java.util.AbstractMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.services.authorization.util.EntityRegionMap;
import org.eclipse.ditto.services.authorization.util.cache.entry.Entry;
import org.eclipse.ditto.services.authorization.util.config.CacheConfigReader;
import org.eclipse.ditto.services.authorization.util.config.CachesConfigReader;
import org.eclipse.ditto.services.models.authorization.EntityId;
import org.eclipse.ditto.services.utils.cache.Cache;
import org.eclipse.ditto.services.utils.cache.CaffeineCache;
import org.eclipse.ditto.signals.commands.policies.PolicyCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.MetricRegistry;
import com.github.benmanes.caffeine.cache.AsyncCacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Caches of entity IDs and enforcer objects.
 */
public final class AuthorizationCaches {


    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizationCaches.class);

    private final Cache<EntityId, Entry<Enforcer>> enforcerCache;
    private final Cache<EntityId, Entry<EntityId>> idCache;

    /**
     * Creates a cache from configuration.
     *
     * @param cachesConfigReader config reader for authorization cache.
     * @param entityRegionMap map resource types to the shard regions of corresponding entities.
     */
    public AuthorizationCaches(final CachesConfigReader cachesConfigReader, final EntityRegionMap entityRegionMap) {

        final Duration askTimeout = cachesConfigReader.askTimeout();

        final EnforcerCacheLoader enforcerCacheLoader = new EnforcerCacheLoader(askTimeout, entityRegionMap);
        enforcerCache = createCache(cachesConfigReader.enforcer(), enforcerCacheLoader,
                new AbstractMap.SimpleImmutableEntry<>("ditto.authorization.enforcer.cache", new MetricRegistry()));

        final IdCacheLoader idCacheLoader = new IdCacheLoader(askTimeout, entityRegionMap, this);
        idCache = createCache(cachesConfigReader.id(), idCacheLoader,
                new AbstractMap.SimpleImmutableEntry<>("ditto.authorization.entityId.cache", new MetricRegistry()));

    }

    /**
     * By an entity cache key, look up the enforcer cache key and the enforcer itself.
     *
     * @param entityKey cache key of an entity.
     * @param consumer handler of cache lookup results.
     */
    public void retrieve(final EntityId entityKey, final BiConsumer<Entry<EntityId>, Entry<Enforcer>> consumer) {
        if (Objects.equals(PolicyCommand.RESOURCE_TYPE, entityKey.getResourceType())) {
            // Enforcer cache key of a policy is always identical to the entity cache key of the policy.
            // No need to save the identity relation in entity cache and waste memory and bandwidth.
            enforcerCache.get(entityKey)
                    .thenAccept(enforcerEntry -> consumer.accept(Entry.permanent(entityKey),
                            enforcerEntry.orElse(null)));
        } else {
            idCache.get(entityKey).thenAccept(enforcerKeyEntryOptional -> {
                if (!enforcerKeyEntryOptional.isPresent()) {
                    // must not happen
                    LOGGER.error("Did not get id-cache value for entityKey <{}>.", entityKey);
                } else {
                    final Entry<EntityId> enforcerKeyEntry = enforcerKeyEntryOptional.get();
                    if (enforcerKeyEntry.exists()) {
                        final EntityId enforcerKey = enforcerKeyEntry.getValue();
                        enforcerCache.get(enforcerKey)
                                .thenAccept(enforcerEntryOptional -> {
                                    if (!enforcerEntryOptional.isPresent()) {
                                        // must not happen
                                        LOGGER.error("Did not get enforcer-cache value for entityKey <{}>.",
                                                enforcerKey);
                                    } else {
                                        final Entry<Enforcer> enforcerEntry = enforcerEntryOptional.get();
                                        consumer.accept(enforcerKeyEntry, enforcerEntry);
                                    }
                                });
                    } else {
                        consumer.accept(enforcerKeyEntry, Entry.nonexistent());
                    }
                }
            });
        }
    }

    /**
     * Invalidate an entry in the entity ID cache.
     *
     * @param resourceKey cache key of the entity.
     */
    public void invalidateEntityId(final EntityId resourceKey) {
        idCache.invalidate(resourceKey);
    }

    private static <K, V> CaffeineCache<K, V> createCache(final CacheConfigReader cacheConfigReader,
            final AsyncCacheLoader<K, V> loader, Map.Entry<String, MetricRegistry> namedMetricRegistry) {
        return CaffeineCache.of(caffeine(cacheConfigReader), loader, namedMetricRegistry);
    }

    private static Caffeine<Object, Object> caffeine(final CacheConfigReader cacheConfigReader) {
        final Caffeine<Object, Object> caffeine = Caffeine.newBuilder();
        caffeine.maximumSize(cacheConfigReader.maximumSize());
        caffeine.expireAfterWrite(cacheConfigReader.expireAfterWrite());
        return caffeine;
    }
}
