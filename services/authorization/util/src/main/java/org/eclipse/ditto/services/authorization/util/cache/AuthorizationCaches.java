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

import static java.util.Objects.requireNonNull;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.eclipse.ditto.model.enforcers.Enforcer;
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
 * Provides access to the caches of enforcers and to the entity-specific caches from entity-id to enforcer-entity-id.
 */
public final class AuthorizationCaches {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizationCaches.class);

    private final Cache<EntityId, Entry<Enforcer>> enforcerCache;
    private final Map<String, Cache<EntityId, Entry<EntityId>>> idCaches;

    /**
     * Creates a cache from configuration.
     *
     * @param cachesConfigReader config reader for authorization cache.
     * @param enforcerCacheLoader the enforcer cache loader.
     * @param enforcementIdCacheLoaders the enforcement id cache loaders.
     * @param metricsReportingConsumer a consumer of named {@link MetricRegistry}s for reporting purposes
     */
    public AuthorizationCaches(final CachesConfigReader cachesConfigReader,
            final AsyncCacheLoader<EntityId, Entry<Enforcer>> enforcerCacheLoader,
            final Map<String, AsyncCacheLoader<EntityId, Entry<EntityId>>> enforcementIdCacheLoaders,
            final Consumer<AbstractMap.Entry<String, MetricRegistry>> metricsReportingConsumer) {
        requireNonNull(cachesConfigReader);
        requireNonNull(enforcerCacheLoader);

        final AbstractMap.SimpleEntry<String, MetricRegistry> namedEnforcerMetricRegistry =
                new AbstractMap.SimpleEntry<>("ditto.authorization.enforcer.cache", new MetricRegistry());
        this.enforcerCache = createCache(cachesConfigReader.enforcer(), enforcerCacheLoader,
                namedEnforcerMetricRegistry);
        metricsReportingConsumer.accept(namedEnforcerMetricRegistry);

        this.idCaches = new HashMap<>();
        enforcementIdCacheLoaders.forEach((resourceType, enforcementIdCacheLoader) -> {
            final String metricName = "ditto.authorization.id.cache." + resourceType;
            final AbstractMap.SimpleEntry<String, MetricRegistry> namedEnforcementIdMetricRegistry =
                    new AbstractMap.SimpleEntry<>(metricName, new MetricRegistry());
            final Cache<EntityId, Entry<EntityId>> enforcementIdCache =
                    createCache(cachesConfigReader.id(), enforcementIdCacheLoader, namedEnforcementIdMetricRegistry);
            idCaches.put(resourceType, enforcementIdCache);
            metricsReportingConsumer.accept(namedEnforcementIdMetricRegistry);
        });
    }

    /**
     * By an entity cache key, look up the enforcer cache key and the enforcer itself.
     *
     * @param entityKey cache key of an entity.
     * @param consumer handler of cache lookup results.
     */
    public void retrieve(final EntityId entityKey, final BiConsumer<Entry<EntityId>, Entry<Enforcer>> consumer) {
        if (Objects.equals(PolicyCommand.RESOURCE_TYPE, entityKey.getResourceType())) {
            retrievePoliciesPolicyEnforcer(entityKey, consumer);
        } else {
            retrievePolicyEnforcer(entityKey, consumer);
        }
    }

    private void retrievePoliciesPolicyEnforcer(final EntityId entityKey,
            final BiConsumer<Entry<EntityId>, Entry<Enforcer>> consumer) {
        // Enforcer cache key of a policy is always identical to the entity cache key of the policy.
        // No need to save the identity relation in entity cache and waste memory and bandwidth.
        enforcerCache.get(entityKey)
                .thenAccept(enforcerEntry -> consumer.accept(Entry.permanent(entityKey),
                        enforcerEntry.orElse(null)));
    }

    private void retrievePolicyEnforcer(final EntityId entityKey,
            final BiConsumer<Entry<EntityId>, Entry<Enforcer>> consumer) {
        getCache(entityKey).get(entityKey).thenAccept(enforcerKeyEntryOptional -> {
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


    /**
     * Invalidate an entry in the entity ID cache.
     *
     * @param resourceKey cache key of the entity.
     */
    public void invalidateEntityId(final EntityId resourceKey) {
        getCache(resourceKey).invalidate(resourceKey);
    }

    /**
     * Invalid a key in all caches.
     *
     * @param cacheKey cache key to invalidate.
     */
    public void invalidateAll(final EntityId cacheKey) {
        getCache(cacheKey).invalidate(cacheKey);
        enforcerCache.invalidate(cacheKey);
    }

    private Cache<EntityId, Entry<EntityId>> getCache(final EntityId entityId) {
        final String resourceType = entityId.getResourceType();
        final Cache<EntityId, Entry<EntityId>> cache = idCaches.get(resourceType);
        if (cache == null) {
            throw new IllegalArgumentException("Unknown resource type=" + resourceType);
        }

        return cache;
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
