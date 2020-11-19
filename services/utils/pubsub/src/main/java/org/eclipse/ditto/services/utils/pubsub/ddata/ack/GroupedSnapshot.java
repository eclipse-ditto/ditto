/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.utils.pubsub.ddata.ack;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Snapshot of {@link GroupedRelation}
 * for {@link org.eclipse.ditto.services.utils.pubsub.actors.Subscriber}.
 */
public final class GroupedSnapshot<K, V> {

    private final Map<V, Set<K>> v2k;
    private final Map<K, String> k2g;

    GroupedSnapshot(final Map<V, Set<K>> v2k, final Map<K, String> k2g) {
        this.v2k = v2k;
        this.k2g = k2g;
    }

    /**
     * Create an empty snapshot of a grouped relation.
     *
     * @param <K> the type of keys.
     * @param <V> the type of values.
     * @return an empty snapshot.
     */
    public static <K, V> GroupedSnapshot<K, V> empty() {
        return new GroupedSnapshot<>(Map.of(), Map.of());
    }

    /**
     * Check if a value is associated with any key.
     *
     * @param value the value.
     * @return whether the value is associated with a key.
     */
    public boolean containsValue(final V value) {
        return v2k.containsKey(value);
    }

    /**
     * Get all keys associated with a value.
     *
     * @param value the value.
     * @return the set of keys associated with a value.
     */
    public Set<K> getKeys(final V value) {
        return v2k.getOrDefault(value, Set.of());
    }

    /**
     * Get the group a key belongs to, if any.
     *
     * @param key the key.
     * @return the group the key belongs to, or an empty optional if the key belongs to no group.
     */
    public Optional<String> getGroup(final K key) {
        return Optional.ofNullable(k2g.get(key));
    }
}
