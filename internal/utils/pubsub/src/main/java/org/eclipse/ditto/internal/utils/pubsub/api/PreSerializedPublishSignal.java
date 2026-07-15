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
package org.eclipse.ditto.internal.utils.pubsub.api;

import java.util.Map;
import java.util.Objects;

import org.eclipse.ditto.base.model.signals.Signal;

/**
 * Wire-transport envelope equivalent to a {@link PublishSignal}, used only for <em>remote</em> fan-out.
 * <p>
 * When one published signal is delivered to several subscriber nodes, Pekko Artery serializes the message once per
 * destination association. For a {@link PublishSignal} that re-serializes the (identical) wrapped signal payload
 * {@code N} times. This envelope carries a {@link SignalBytesHolder} shared across all {@code N} destinations, so the
 * signal payload is serialized exactly once (by {@code PreSerializedPublishSignalSerializer}); only the small,
 * per-destination {@code groups} map differs between envelopes.
 * <p>
 * This type never reaches a {@code Subscriber} actor as-is: on the receiving node the serializer reconstructs a plain
 * {@link PublishSignal}, and local (same-node) subscribers convert it back to a {@link PublishSignal} on receipt. It
 * is therefore an internal transport type and deliberately not a {@code Jsonifiable}/{@code Command}.
 *
 * @since 3.9.4
 */
public final class PreSerializedPublishSignal {

    private final SignalBytesHolder holder;
    private final Map<String, Integer> groups;
    private final String groupIndexKey;

    private PreSerializedPublishSignal(final SignalBytesHolder holder, final Map<String, Integer> groups,
            final String groupIndexKey) {
        this.holder = holder;
        this.groups = Map.copyOf(groups);
        this.groupIndexKey = groupIndexKey;
    }

    /**
     * Create an envelope for one subscriber destination.
     *
     * @param holder the shared holder memoizing the serialized signal across all destinations of this publication.
     * @param groups the per-destination group-to-size relation (see {@link PublishSignal#getGroups()}).
     * @param groupIndexKey the key deciding which group member receives the signal.
     * @return the envelope.
     */
    public static PreSerializedPublishSignal of(final SignalBytesHolder holder, final Map<String, Integer> groups,
            final CharSequence groupIndexKey) {
        return new PreSerializedPublishSignal(holder, groups, groupIndexKey.toString());
    }

    /**
     * @return the shared holder for the (memoized) serialized signal.
     */
    public SignalBytesHolder getHolder() {
        return holder;
    }

    /**
     * @return the signal being published (for local, non-serialized delivery / diagnostics).
     */
    public Signal<?> getSignal() {
        return holder.getSignal();
    }

    /**
     * @return the per-destination group-to-size relation.
     */
    public Map<String, Integer> getGroups() {
        return groups;
    }

    /**
     * @return the group index key.
     */
    public String getGroupIndexKey() {
        return groupIndexKey;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof final PreSerializedPublishSignal that)) {
            return false;
        }
        return Objects.equals(holder, that.holder) && Objects.equals(groups, that.groups) &&
                Objects.equals(groupIndexKey, that.groupIndexKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(holder, groups, groupIndexKey);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() +
                "[signal=" + holder.getSignal() +
                ", groups=" + groups +
                ", groupIndexKey=" + groupIndexKey + "]";
    }
}
