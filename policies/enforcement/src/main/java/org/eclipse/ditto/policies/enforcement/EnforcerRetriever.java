/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.enforcement;

import static java.util.Objects.requireNonNull;

import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.base.model.exceptions.DittoInternalErrorException;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.internal.utils.cache.Cache;
import org.eclipse.ditto.internal.utils.cache.entry.Entry;
import org.eclipse.ditto.internal.utils.cacheloaders.EnforcementCacheKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Retrieves an enforcer by using an ID Cache and Enforcer Cache.
 * TODO CR-11297 candidate for removal
 *
 * @param <E> the type of the enforcer cache {@link Entry}'s value
 */
public final class EnforcerRetriever<E> {

    private static final Logger LOGGER = LoggerFactory.getLogger(EnforcerRetriever.class);

    private final Cache<EnforcementCacheKey, Entry<EnforcementCacheKey>> idCache;
    private final Function<EntityType, Cache<EnforcementCacheKey, Entry<E>>> enforcerCacheFunction;

    /**
     * Constructor.
     *
     * @param idCache the ID Cache.
     * @param enforcerCache the Enforcer Cache.
     */
    public EnforcerRetriever(
            final Cache<EnforcementCacheKey, Entry<EnforcementCacheKey>> idCache,
            final Cache<EnforcementCacheKey, Entry<E>> enforcerCache) {
        this(idCache, resourceType -> requireNonNull(enforcerCache));
    }

    /**
     * Constructor.
     *
     * @param idCache the ID Cache.
     * @param enforcerCacheFunction a function to determine a Enforcer Cache for a resource type.
     */
    private EnforcerRetriever(
            final Cache<EnforcementCacheKey, Entry<EnforcementCacheKey>> idCache,
            final Function<EntityType, Cache<EnforcementCacheKey, Entry<E>>> enforcerCacheFunction) {
        this.idCache = requireNonNull(idCache);
        this.enforcerCacheFunction = requireNonNull(enforcerCacheFunction);
    }

    /**
     * Constructor.
     *
     * @param idCache the ID Cache.
     * @param enforcerCaches the Enforcer Caches per resource type.
     */
    public EnforcerRetriever(
            final Cache<EnforcementCacheKey, Entry<EnforcementCacheKey>> idCache,
            final Map<EntityType, Cache<EnforcementCacheKey, Entry<E>>> enforcerCaches) {
        this(idCache, requireNonNull(enforcerCaches)::get);
    }

    /**
     * By an entity cache key, look up the enforcer cache key and the enforcer itself.
     *
     * @param entityKey cache key of an entity.
     * @param handler handler of cache lookup results.
     * @return future after retrieved cache entries are given to the consumer.
     */
    public CompletionStage<Contextual<WithDittoHeaders>> retrieve(final EnforcementCacheKey entityKey,
            final BiFunction<Entry<EnforcementCacheKey>, Entry<E>, CompletionStage<Contextual<WithDittoHeaders>>> handler) {
        return idCache.get(entityKey).thenCompose(enforcerKeyEntryOptional -> {
            if (enforcerKeyEntryOptional.isEmpty()) {
                // may happen due to namespace blocking
                LOGGER.info("Did not get id-cache value for entityKey <{}>.", entityKey);
                return handler.apply(Entry.nonexistent(), Entry.nonexistent());
            } else {
                final Entry<EnforcementCacheKey> enforcerKeyEntry = enforcerKeyEntryOptional.get();
                if (enforcerKeyEntry.exists()) {
                    final EnforcementCacheKey enforcerKey = enforcerKeyEntry.getValueOrThrow();
                    final EntityType entityType = enforcerKey.getId().getEntityType();
                    final Cache<EnforcementCacheKey, Entry<E>> enforcerCache =
                            enforcerCacheFunction.apply(entityType);
                    if (enforcerCache == null) {
                        LOGGER.error("No enforcerCache for entity type: <{}>", entityType);
                        throw DittoInternalErrorException.newBuilder()
                                .build();
                    }
                    return retrieveByEnforcerKey(enforcerKey, enforcerEntry ->
                            handler.apply(enforcerKeyEntry, enforcerEntry));
                } else {
                    return handler.apply(enforcerKeyEntry, Entry.nonexistent());
                }
            }
        });
    }

    /**
     * Lookup the enforcer by its own key (as opposed to the key of an entity it governs).
     *
     * @param enforcerKey key of the enforcer.
     * @param handler what to do with the enforcer.
     */
    public CompletionStage<Contextual<WithDittoHeaders>> retrieveByEnforcerKey(
            final EnforcementCacheKey enforcerKey,
            final Function<Entry<E>, CompletionStage<Contextual<WithDittoHeaders>>> handler) {
        final EntityType entityType = enforcerKey.getId().getEntityType();
        final Cache<EnforcementCacheKey, Entry<E>> enforcerCache = enforcerCacheFunction.apply(entityType);
        if (enforcerCache == null) {
            throw new IllegalStateException("No enforcerCache for entity type: " + entityType);
        }
        return enforcerCache.get(enforcerKey)
                .thenCompose(enforcerEntryOptional -> {
                    if (enforcerEntryOptional.isEmpty()) {
                        // may happen due to namespace blocking
                        LOGGER.info("Did not get enforcer-cache value for entityKey <{}>.", enforcerKey);
                        return handler.apply(Entry.nonexistent());
                    } else {
                        final Entry<E> enforcerEntry = enforcerEntryOptional.get();
                        return handler.apply(enforcerEntry);
                    }
                });
    }

}
