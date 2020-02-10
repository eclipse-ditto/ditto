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
package org.eclipse.ditto.services.utils.cache;

import java.util.Optional;

import org.eclipse.ditto.model.base.entity.id.EntityId;

/**
 * Entity ID together with resource type.
 */
public interface EntityIdWithResourceType {

    /**
     * Retrieve the resource type.
     *
     * @return the resource type.
     */
    String getResourceType();

    /**
     * Retrieve the ID.
     *
     * @return the ID.
     */
    EntityId getId();

    /**
     * Retrieve the optional context to use when doing the cache lookup.
     *
     * @return the cache context to use for lookup.
     */
    Optional<CacheLookupContext> getCacheLookupContext();

    /**
     * Serialize this object as string.
     *
     * @return serialized form of this object.
     */
    String toString();

    /**
     * Create a new entity ID from the given  {@code resourceType} and {@code id}.
     *
     * @param resourceType the resource type.
     * @param id the entity ID.
     * @return the entity ID with resource type object.
     */
    static EntityIdWithResourceType of(final String resourceType, final EntityId id) {
        return CacheFactory.newEntityId(resourceType, id);
    }

    /**
     * Create a new entity ID from the given  {@code resourceType} and {@code id}.
     *
     * @param resourceType the resource type.
     * @param id the entity ID.
     * @param cacheLookupContext additional context information to use for the cache lookup.
     * @return the entity ID with resource type object.
     */
    static EntityIdWithResourceType of(final String resourceType, final EntityId id,
            final CacheLookupContext cacheLookupContext) {
        return CacheFactory.newEntityId(resourceType, id, cacheLookupContext);
    }

    /**
     * Deserialize entity ID with resource type from a string.
     *
     * @param string the string.
     * @return the entity ID with resource type.
     * @throws IllegalArgumentException if the string does not have the expected format.
     */
    static EntityIdWithResourceType readFrom(final String string) {
        return CacheFactory.readEntityIdFrom(string);
    }

}
