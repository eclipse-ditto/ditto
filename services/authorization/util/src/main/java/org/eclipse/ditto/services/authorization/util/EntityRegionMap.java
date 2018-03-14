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
package org.eclipse.ditto.services.authorization.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import akka.actor.ActorRef;

/**
 * Immutable sharable map from resource types to actor reference.
 */
@Immutable
public final class EntityRegionMap {

    private final Map<String, ActorRef> rawMap;

    private EntityRegionMap(final Builder builder) {
        rawMap = Collections.unmodifiableMap(new HashMap<>(builder.hashMap));
    }

    /**
     * Find entity region for a resource type.
     *
     * @param resourceType resource type of an entity.
     * @return actor reference to the shard region of the entity.
     */
    public Optional<ActorRef> lookup(final String resourceType) {
        return Optional.ofNullable(rawMap.get(resourceType));
    }

    /**
     * Create a new builder of {@code EntityRegionMap}.
     *
     * @return a new builder.
     */
    public static Builder newBuilder() {
        return new Builder();
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
