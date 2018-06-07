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
package org.eclipse.ditto.services.concierge.enforcement;

import static java.util.Objects.requireNonNull;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.services.models.concierge.EntityId;
import org.eclipse.ditto.services.models.concierge.cache.Entry;
import org.eclipse.ditto.services.utils.cache.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Retrieves an enforcer by using an ID Cache and Enforcer Cache.
 */
public final class EnforcerRetriever {

    private static final Logger LOGGER = LoggerFactory.getLogger(EnforcerRetriever.class);

    private final Cache<EntityId, Entry<EntityId>> idCache;
    private final Function<String, Cache<EntityId, Entry<Enforcer>>> enforcerCacheFunction;

    /**
     * Constructor.
     *
     * @param idCache the ID Cache.
     * @param enforcerCache the Enforcer Cache.
     */
    public EnforcerRetriever(
            final Cache<EntityId, Entry<EntityId>> idCache,
            final Cache<EntityId, Entry<Enforcer>> enforcerCache) {
        this(idCache, resourceType -> requireNonNull(enforcerCache));
    }

    /**
     * Constructor.
     *
     * @param idCache the ID Cache.
     * @param enforcerCacheFunction a function to determine a Enforcer Cache for a resource type.
     */
    private EnforcerRetriever(
            final Cache<EntityId, Entry<EntityId>> idCache,
            final Function<String, Cache<EntityId, Entry<Enforcer>>> enforcerCacheFunction) {
        this.idCache = requireNonNull(idCache);
        this.enforcerCacheFunction = requireNonNull(enforcerCacheFunction);
    }

    /**
     * Constructor.
     *
     * @param idCache the ID Cache.
     * @param enforcerCaches the Enforcer Caches per resource type.
     */
    EnforcerRetriever(
            final Cache<EntityId, Entry<EntityId>> idCache,
            final Map<String, Cache<EntityId, Entry<Enforcer>>> enforcerCaches) {
        this(idCache, enforcerCaches::get);
    }

    /**
     * By an entity cache key, look up the enforcer cache key and the enforcer itself.
     *
     * @param entityKey cache key of an entity.
     * @param consumer handler of cache lookup results.
     */
    public void retrieve(final EntityId entityKey, final BiConsumer<Entry<EntityId>, Entry<Enforcer>> consumer) {
        idCache.get(entityKey).thenAccept(enforcerKeyEntryOptional -> {
            if (!enforcerKeyEntryOptional.isPresent()) {
                // must not happen
                LOGGER.error("Did not get id-cache value for entityKey <{}>.", entityKey);
            } else {
                final Entry<EntityId> enforcerKeyEntry = enforcerKeyEntryOptional.get();
                if (enforcerKeyEntry.exists()) {
                    final EntityId enforcerKey = enforcerKeyEntry.getValue();
                    final String resourceType = enforcerKey.getResourceType();
                    final Cache<EntityId, Entry<Enforcer>> enforcerCache =
                            enforcerCacheFunction.apply(resourceType);
                    if (enforcerCache == null) {
                        throw new IllegalStateException("No enforcerCache for resource type: " + resourceType);
                    }
                    retrieveByEnforcerKey(enforcerKey, enforcerEntry ->
                            consumer.accept(enforcerKeyEntry, enforcerEntry));
                } else {
                    consumer.accept(enforcerKeyEntry, Entry.nonexistent());
                }
            }
        });
    }

    /**
     * Lookup the enforcer by its own key (as opposed to the key of an entity it governs).
     *
     * @param enforcerKey key of the enforcer.
     * @param consumer what to do with the enforcer.
     */
    public void retrieveByEnforcerKey(final EntityId enforcerKey, final Consumer<Entry<Enforcer>> consumer) {
        final String resourceType = enforcerKey.getResourceType();
        final Cache<EntityId, Entry<Enforcer>> enforcerCache =
                enforcerCacheFunction.apply(resourceType);
        if (enforcerCache == null) {
            throw new IllegalStateException("No enforcerCache for resource type: " + resourceType);
        }
        enforcerCache.get(enforcerKey)
                .thenAccept(enforcerEntryOptional -> {
                    if (!enforcerEntryOptional.isPresent()) {
                        // must not happen
                        LOGGER.error("Did not get enforcer-cache value for entityKey <{}>.",
                                enforcerKey);
                    } else {
                        final Entry<Enforcer> enforcerEntry = enforcerEntryOptional.get();
                        consumer.accept(enforcerEntry);
                    }
                });

    }

}
