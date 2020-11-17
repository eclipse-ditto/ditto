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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotEmpty;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * The union of disjoint complete bipartite graphs where each connected component has an optional "group" name.
 * In other words, a mutable relation between keys and values such that:
 * <ol>
 * <li>Each key and each value belongs to zero or one group,</li>
 * <li>All keys belonging to one group are associated with the same values,</li>
 * <li>Keys in different groups are associated with disjoint values,</li>
 * <li>Keys not in any group are each associated with a unique set of values.</li>
 * </ol>
 *
 * @param <K> type of keys.
 * @param <V> type of values.
 */
@NotThreadSafe
public final class GroupedRelation<K, V> {

    private final Map<K, Grouped<V>> k2v = new HashMap<>();
    private final Map<V, Grouped<K>> v2k = new HashMap<>();
    private final Map<String, Set<V>> g2v = new HashMap<>();

    private GroupedRelation() {}

    /**
     * Create an empty grouped relation.
     *
     * @param <K> the type of keys.
     * @param <V> the type of values.
     * @return a new empty grouped relation.
     */
    public static <K, V> GroupedRelation<K, V> create() {
        return new GroupedRelation<>();
    }

    /**
     * Associate a key with a set of values such that none belongs to a group.
     *
     * @param key the key.
     * @param values the values.
     */
    public void put(final K key, final Set<V> values) {
        doPut(key, null, values);
    }

    /**
     * Associate a key with a set of values and a group.
     *
     * @param key the key.
     * @param group the group, or null.
     * @param values the values.
     */
    public void put(final K key, @Nullable final String group, final Set<V> values) {
        doPut(key, group, values);
    }

    /**
     * Retrieve grouped values associated with a key.
     *
     * @param key the key.
     * @return the grouped values associated with it, or an empty optional if no values are associated.
     */
    public Optional<Grouped<V>> getValues(K key) {
        return Optional.ofNullable(k2v.get(key));
    }

    /**
     * Retrieve the values associated to a key by the group the key belongs to.
     *
     * @param group the group.
     * @return the set of values associated to every key belonging to that group, or an empty optional.
     */
    public Optional<Set<V>> getValuesOfGroup(final String group) {
        return Optional.ofNullable(g2v.get(group));
    }

    /**
     * Retrieve the set of keys associated with a value with an optional group name.
     *
     * @param value the value.
     * @return the grouped set of keys.
     */
    public Optional<Grouped<K>> getKeys(V value) {
        return Optional.ofNullable(v2k.get(value));
    }

    /**
     * Create an immutable snapshot of this mutable relation.
     *
     * @return the snapshot.
     */
    public GroupedSnapshot<K, V> export() {
        return new GroupedSnapshot<>(indexKeysByValue(), indexGroupsByKey());
    }

    /**
     * Stream all grouped values.
     *
     * @return the grouped values.
     */
    public Stream<Grouped<V>> streamGroupedValues() {
        return k2v.values().stream();
    }

    /**
     * Check if a value is associated with any key.
     *
     * @param value the value.
     * @return whether it is associated with any key.
     */
    public boolean containsValue(final V value) {
        return v2k.containsKey(value);
    }

    /**
     * Remove a subscriber and all its declared ack labels and groups.
     *
     * @param key the key
     */
    public void removeKey(K key) {
        final Grouped<V> grouped = k2v.remove(key);
        if (grouped != null) {
            for (final V value : grouped.getValues()) {
                v2k.computeIfPresent(value, (v, keys) -> {
                    keys.getValues().remove(key);
                    if (keys.getValues().isEmpty()) {
                        return null;
                    } else {
                        return keys;
                    }
                });
            }
            grouped.getGroup().ifPresent(g2v::remove);
        }
    }

    private void doPut(final K key, @Nullable final String group, final Set<V> values) {
        checkNotEmpty(values, "values");
        k2v.put(key, Grouped.of(group, values));
        values.forEach(value -> v2k.compute(value, (v, grouped) -> {
            if (grouped == null) {
                return Grouped.of(group, new HashSet<>());
            } else {
                grouped.getValues().add(key);
                return grouped;
            }
        }));
        if (group != null) {
            g2v.put(group, values);
        }
    }

    private Map<V, Set<K>> indexKeysByValue() {
        return v2k.entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> Set.copyOf(entry.getValue().getValues())));
    }

    private Map<K, String> indexGroupsByKey() {
        return k2v.entrySet()
                .stream()
                .filter(entry -> entry.getValue().getGroup().isPresent())
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getGroup().orElseThrow()));
    }
}
