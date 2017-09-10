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
package org.eclipse.ditto.services.utils.distributedcache.actors;

import static java.util.Objects.requireNonNull;

import java.util.Optional;

/**
 * An implementation of this interface might have an arbitrary context which can be used for correlation.
 */
public interface WithContext {

    /**
     * Returns the arbitrary context which can be used for correlation.
     *
     * @return the context or an empty Optional.
     */
    Optional<Object> getContext();

    /**
     * Indicates whether this entity has the same context like the specified CacheType.
     * 
     * @param cacheType the CacheType which provides the expected context.
     * @return {@code true} if this entity has the same context like {@code cacheType}, {@code false} else.
     * @throws NullPointerException if {@code cacheType} is {@code null}.
     */
    default boolean hasContextOfType(final CacheType cacheType) {
        requireNonNull(cacheType, "The cache type providing the expected context must not be null!");
        final String expectedContext = cacheType.getContext();
        return getContext()
                .filter(expectedContext::equals)
                .isPresent();
    }

}
