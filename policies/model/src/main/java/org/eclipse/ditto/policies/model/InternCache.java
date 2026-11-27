/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.model;

import javax.annotation.concurrent.ThreadSafe;

/**
 * A small, lossy, direct-mapped cache which shares the instances of immutable values that repeat very often, e.g. the
 * permissions of the resources of policy entries.
 * <p>
 * A colliding value replaces the previously cached one, so the cache stays bounded and rarely used values cannot
 * occupy it permanently. Lookups never block; races are benign because only immutable values with final fields are
 * cached.
 * </p>
 *
 * @param <T> the type of the cached immutable values.
 */
@ThreadSafe
final class InternCache<T> {

    private final Object[] slots;
    private final int mask;

    /**
     * @param size the number of slots, must be a power of two.
     */
    InternCache(final int size) {
        if (Integer.bitCount(size) != 1) {
            throw new IllegalArgumentException("The size must be a power of two: " + size);
        }
        slots = new Object[size];
        mask = size - 1;
    }

    /**
     * Returns the cached instance equal to {@code candidate} or caches and returns {@code candidate}.
     *
     * @param candidate the immutable value to share.
     * @return an instance equal to {@code candidate}.
     */
    @SuppressWarnings("unchecked")
    T intern(final T candidate) {
        final int hash = candidate.hashCode();
        final int slot = (hash ^ (hash >>> 16)) & mask;
        final Object cached = slots[slot];
        if (candidate.equals(cached)) {
            return (T) cached;
        }
        slots[slot] = candidate;
        return candidate;
    }

}
