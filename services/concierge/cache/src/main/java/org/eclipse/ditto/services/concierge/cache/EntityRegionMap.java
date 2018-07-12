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
package org.eclipse.ditto.services.concierge.cache;

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

import akka.actor.ActorRef;

/**
 * Immutable sharable map from resource types to actor reference.
 */
@Immutable
public final class EntityRegionMap implements Function<String, ActorRef> {

    private final Map<String, ActorRef> rawMap;

    private EntityRegionMap(final Map<String, ActorRef> rawMap) {
        this.rawMap = Collections.unmodifiableMap(new HashMap<>(rawMap));
    }

    private EntityRegionMap(final Builder builder) {
        this(builder.hashMap);
    }

    /**
     * Find entity region for a resource type.
     *
     * @param resourceType resource type of an entity.
     * @return actor reference to the shard region of the entity.
     */
    public Optional<ActorRef> lookup(final String resourceType) {
        requireNonNull(resourceType);

        return Optional.ofNullable(findRegion(resourceType));
    }

    @Nullable
    private ActorRef findRegion(final String resourceType) {
        return rawMap.get(resourceType);
    }

    @Override
    @Nullable
    public ActorRef apply(final String resourceType) {
        requireNonNull(resourceType);

        return findRegion(resourceType);
    }

    /**
     * Creates an {@link EntityRegionMap} with a single entry.
     *
     * @param resourceType the resource type.
     * @param targetActor the actor reference.
     * @return the created {@link EntityRegionMap}.
     */
    public static EntityRegionMap singleton(final String resourceType, final ActorRef targetActor) {
        return new EntityRegionMap(Collections.singletonMap(resourceType, targetActor));
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

        private final HashMap<String, ActorRef> hashMap = new HashMap<>();

        /**
         * Add a resource-type-to-actor-reference mapping.
         *
         * @param resourceType the resource type.
         * @param targetActor the actor reference.
         * @return this builder.
         */
        public Builder put(final String resourceType, final ActorRef targetActor) {
            requireNonNull(resourceType);
            requireNonNull(targetActor);

            hashMap.put(resourceType, targetActor);
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
