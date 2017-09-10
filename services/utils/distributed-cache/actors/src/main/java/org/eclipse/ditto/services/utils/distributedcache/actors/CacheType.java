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

/**
 * Enum of the different types of caches.
 */
public enum CacheType {

    /**
     * Cache type for topology-related cache entities with context {@code "topology"}.
     */
    TOPOLOGY("topology"),

    /**
     * Cache type for policy-related cache entities with context {@code "policy"}.
     */
    POLICY("policy"),

    /**
     * Cache type for thing-related cache entities with context {@code "thing"}.
     */
    THING("thing");

    private final String context;

    CacheType(final String context) {
        this.context = requireNonNull(context, "The context must not be null!");
    }

    /**
     * Returns the context of this cache type.
     *
     * @return the context.
     */
    public String getContext() {
        return context;
    }

    /**
     * Same as {@link #getContext()}.
     *
     * @return the context of this cache type.
     */
    @Override
    public String toString() {
        return context;
    }

}
