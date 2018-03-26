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
package org.eclipse.ditto.services.authorization.util.update;

import static java.util.Objects.requireNonNull;

import org.eclipse.ditto.services.authorization.util.cache.AuthorizationCaches;

/**
 * Abstract base implementation for {@link CacheUpdateStrategy} providing access to {@link AuthorizationCaches}.
 * @param <T> the type of events to be handled by this strategy.
 */
public abstract class AbstractCacheUpdateStrategy<T> implements CacheUpdateStrategy<T> {
    private final AuthorizationCaches caches;

    /**
     * Constructor.
     * @param caches the caches.
     */
    public AbstractCacheUpdateStrategy(final AuthorizationCaches caches) {
        this.caches = requireNonNull(caches);
    }

    /**
     * Returns the caches.
     * @return the caches.
     */
    public AuthorizationCaches getCaches() {
        return caches;
    }

}
