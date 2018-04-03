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
package org.eclipse.ditto.services.authorization.util.enforcement;

import static java.util.Objects.requireNonNull;

import java.util.function.BiConsumer;

import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.services.authorization.util.cache.entry.Entry;
import org.eclipse.ditto.services.models.authorization.EntityId;
import org.eclipse.ditto.services.utils.cache.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Retrieves an enforcer by using an ID Cache and Enforcer Cache.
 */
public final class EnforcerRetriever {
    private static final Logger LOGGER = LoggerFactory.getLogger(EnforcerRetriever.class);

    private final Cache<EntityId, Entry<EntityId>> idCache;
    private final Cache<EntityId, Entry<Enforcer>> enforcerCache;

    /**
     * Constructor.
     *
     * @param idCache the ID Cache.
     * @param enforcerCache the Enforcer Cache.
     */
    public EnforcerRetriever(
            final Cache<EntityId, Entry<EntityId>> idCache,
            final Cache<EntityId, Entry<Enforcer>> enforcerCache) {
        this.idCache = requireNonNull(idCache);
        this.enforcerCache = requireNonNull(enforcerCache);
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
