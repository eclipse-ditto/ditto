/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.cacheloaders;

import static java.util.Objects.requireNonNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.entity.type.EntityType;

import akka.actor.ActorRef;

/**
 * Immutable sharable map from resource types to actor reference.
 */
@Immutable
public final class EntityRegionMap implements Function<EntityType, ActorRef> {

    private final Map<EntityType, ActorRef> rawMap;

    private EntityRegionMap(final Map<EntityType, ActorRef> rawMap) {
        this.rawMap = Collections.unmodifiableMap(new HashMap<>(rawMap));
    }

    private EntityRegionMap(final Builder builder) {
        this(builder.hashMap);
    }

    /**
     * Find entity region for a resource type.
     *
     * @param entityType type of an entity.
     * @return actor reference to the shard region of the entity.
     */
    public Optional<ActorRef> lookup(final EntityType entityType) {
        requireNonNull(entityType);

        return Optional.ofNullable(findRegion(entityType));
    }

    @Nullable
    private ActorRef findRegion(final EntityType entityType) {
        return rawMap.get(entityType);
    }

    @Override
    @Nullable
    public ActorRef apply(final EntityType entityType) {
        requireNonNull(entityType);

        return findRegion(entityType);
    }

    /**
     * Creates an {@link EntityRegionMap} with a single entry.
     *
     * @param entityType the resource type.
     * @param targetActor the actor reference.
     * @return the created {@link EntityRegionMap}.
     */
    public static EntityRegionMap singleton(final EntityType entityType, final ActorRef targetActor) {
        return new EntityRegionMap(Collections.singletonMap(entityType, targetActor));
    }

    /**
     * Create a new builder of {@code EntityRegionMap}.
     *
     * @return a new builder.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final EntityRegionMap that = (EntityRegionMap) o;
        return Objects.equals(rawMap, that.rawMap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rawMap);
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + " [" +
                "rawMap=" + rawMap +
                ']';
    }

    /**
     * Builder of {@code EntityRegionMap}.
     */
    @NotThreadSafe
    public static final class Builder {

        private final HashMap<EntityType, ActorRef> hashMap = new HashMap<>();

        /**
         * Add a resource-type-to-actor-reference mapping.
         *
         * @param entityType the entity type.
         * @param targetActor the actor reference.
         * @return this builder.
         */
        public Builder put(final EntityType entityType, final ActorRef targetActor) {
            requireNonNull(entityType);
            requireNonNull(targetActor);

            hashMap.put(entityType, targetActor);
            return this;
        }

        /**
         * Create an {@code EntityRegionMap} from this builder.
         *
         * @return the {@code EntityRegionMap}.
         */
        public EntityRegionMap build() {
            return new EntityRegionMap(this);
        }
    }
}
