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

import java.util.Collection;
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

    // TODO: javadoc
    public boolean containsValue(final V value) {
        return v2k.containsKey(value);
    }

    // TODO: javadoc
    public Collection<K> getKeys(final V value) {
        return v2k.getOrDefault(value, Set.of());
    }

    // TODO: javadoc
    public Optional<String> getGroup(final K key) {
        return Optional.ofNullable(k2g.get(key));
    }
}
