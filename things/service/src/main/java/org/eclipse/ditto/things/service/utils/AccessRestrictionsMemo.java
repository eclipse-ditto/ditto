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
package org.eclipse.ditto.things.service.utils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import javax.annotation.concurrent.ThreadSafe;

import org.eclipse.ditto.policies.enforcement.PolicyEnforcer;

/**
 * Small, thread-safe LRU memo of {@link AccessRestrictions}. They only depend on the effective policy and the
 * structure of the Thing JSON (see {@link PartialAccessPathCalculator#structureHash(org.eclipse.ditto.json.JsonObject)}),
 * so they are keyed by the {@link PolicyEnforcer} instance, which is replaced on every change of the effective
 * policy (also of imported and namespace root policies), compared by identity.
 *
 * @since 3.9.9
 */
@ThreadSafe
public final class AccessRestrictionsMemo {

    private final Map<Key, AccessRestrictions> entries;

    /**
     * @param maxSize the maximum number of memoized entries.
     */
    public AccessRestrictionsMemo(final int maxSize) {
        entries = new LinkedHashMap<>(maxSize + 1, 1f, true) {
            @Override
            protected boolean removeEldestEntry(final Map.Entry<Key, AccessRestrictions> eldest) {
                return size() > maxSize;
            }
        };
    }

    /**
     * Returns the memoized restrictions or calculates and memoizes them.
     *
     * @param policyEnforcer the policy enforcer the restrictions are calculated with.
     * @param structureHash the structure hash of the Thing JSON the restrictions are calculated for.
     * @param withPartialAccessPaths whether the restrictions include partial READ access.
     * @param calculation the calculation of the restrictions, called outside any lock.
     * @return the restrictions.
     */
    public AccessRestrictions get(final PolicyEnforcer policyEnforcer, final long structureHash,
            final boolean withPartialAccessPaths, final Supplier<AccessRestrictions> calculation) {

        final Key key = new Key(policyEnforcer, structureHash, withPartialAccessPaths);
        synchronized (entries) {
            final AccessRestrictions cached = entries.get(key);
            if (cached != null) {
                return cached;
            }
        }
        final AccessRestrictions calculated = calculation.get();
        synchronized (entries) {
            // a new enforcer replaces the previous one: do not keep outdated policies reachable
            entries.keySet().removeIf(cachedKey -> cachedKey.policyEnforcer != policyEnforcer);
            entries.put(key, calculated);
        }
        return calculated;
    }

    private record Key(PolicyEnforcer policyEnforcer, long structureHash, boolean withPartialAccessPaths) {

        @Override
        public boolean equals(final Object o) {
            return o instanceof Key other &&
                    policyEnforcer == other.policyEnforcer &&
                    structureHash == other.structureHash &&
                    withPartialAccessPaths == other.withPartialAccessPaths;
        }

        @Override
        public int hashCode() {
            return 31 * (31 * System.identityHashCode(policyEnforcer) + Long.hashCode(structureHash)) +
                    Boolean.hashCode(withPartialAccessPaths);
        }
    }

}
