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
package org.eclipse.ditto.internal.utils.pubsub.ddata.ack;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Snapshot of {@link GroupedRelation}
 * for {@link org.eclipse.ditto.internal.utils.pubsub.actors.Subscriber}.
 */
public final class GroupedSnapshot<K, V> {

    private final Map<V, Set<K>> v2k;
    private final Map<String, Set<V>> g2v;

    GroupedSnapshot(final Map<V, Set<K>> v2k, final Map<String, Set<V>> g2v) {
        this.v2k = v2k;
        this.g2v = g2v;
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
     * Get all keys associated with a value.
     *
     * @param value the value.
     * @return the set of keys associated with a value.
     */
    public Set<K> getKeys(final V value) {
        return v2k.getOrDefault(value, Set.of());
    }

    /**
     * Get all values associated with some group in the given collection and values associated with no group.
     *
     * @return all specified values.
     */
    public Set<V> getValues(final Collection<String> groups) {
        if (groups.isEmpty()) {
            return g2v.getOrDefault("", Set.of());
        } else {
            return Stream.concat(Stream.of(""), groups.stream())
                    .flatMap(group -> g2v.getOrDefault(group, Set.of()).stream())
                    .collect(Collectors.toSet());
        }
    }

    /**
     * Estimate the size of this snapshot in bytes in the distributed data.
     *
     * @return the estimated size.
     */
    public long estimateSize() {
        return g2v.entrySet()
                .stream()
                .mapToLong(entry -> entry.getKey().length() +
                        entry.getValue().stream().mapToLong(value -> value.toString().length()).sum())
                .sum();
    }

}
