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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.entity.id.DefaultEntityId;
import org.eclipse.ditto.model.base.entity.id.EntityId;

/**
 * Implementation of {@code EntityIdWithResourceType}.
 */
@Immutable
final class ImmutableEntityIdWithResourceType implements EntityIdWithResourceType {

    static final String DELIMITER = ":";

    private final String resourceType;
    private final EntityId id;
    @Nullable private final CacheLookupContext cacheLookupContext;

    /**
     * Creates a new {@code ImmutableEntityIdWithResourceType}.
     *
     * @param resourceType the resource type.
     * @param id the entity id.
     * @param cacheLookupContext additional context information to use for the cache lookup.
     * @throws IllegalArgumentException if resource type contains ':'.
     */
    private ImmutableEntityIdWithResourceType(
            final String resourceType,
            final EntityId id,
            @Nullable final CacheLookupContext cacheLookupContext) {
        this.resourceType = checkNotNull(resourceType, "resourceType");
        // build a default entity id, so that serializing and deserializing works properly
        this.id = DefaultEntityId.of(checkNotNull(id, "id"));
        this.cacheLookupContext = cacheLookupContext;
        if (resourceType.contains(DELIMITER)) {
            final String message =
                    String.format("Resource type <%s> may not contain ':'. Id = <%s>", resourceType, id);
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Create a new entity ID from the given  {@code resourceType} and {@code id}.
     *
     * @param resourceType the resource type.
     * @param id the entity ID.
     * @return the entity ID with resource type object.
     */
    static EntityIdWithResourceType of(final String resourceType, final EntityId id) {
        return new ImmutableEntityIdWithResourceType(resourceType, id, null);
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
        return new ImmutableEntityIdWithResourceType(resourceType, id, cacheLookupContext);
    }

    /**
     * Deserialize entity ID with resource type from a string.
     *
     * @param string the string.
     * @return the entity ID with resource type.
     * @throws IllegalArgumentException if the string does not have the expected format.
     */
    static EntityIdWithResourceType readFrom(final String string) {
        checkNotNull(string, "string");

        final int delimiterIndex = string.indexOf(DELIMITER);
        if (delimiterIndex < 0) {
            final String message = MessageFormat.format("Unexpected EntityId format: <{0}>", string);
            throw new IllegalArgumentException(message);
        } else {
            final EntityId id = DefaultEntityId.of(string.substring(delimiterIndex + 1));
            final String resourceType = string.substring(0, delimiterIndex);
            return new ImmutableEntityIdWithResourceType(resourceType, id, null);
        }
    }

    @Override
    public String getResourceType() {
        return resourceType;
    }

    @Override
    public EntityId getId() {
        return id;
    }

    @Override
    public Optional<CacheLookupContext> getCacheLookupContext() {
        return Optional.ofNullable(cacheLookupContext);
    }

    @Override
    public boolean equals(final Object o) {
        if (o instanceof ImmutableEntityIdWithResourceType) {
            final ImmutableEntityIdWithResourceType that = (ImmutableEntityIdWithResourceType) o;
            return Objects.equals(resourceType, that.resourceType) &&
                    Objects.equals(id, that.id) &&
                    Objects.equals(cacheLookupContext, that.cacheLookupContext);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(resourceType, id, cacheLookupContext);
    }

    @Override
    public String toString() {
        // cache context is not in the string representation
        // because it is used for serialization and cache context is local
        return String.format("%s%s%s", resourceType, DELIMITER, id);
    }

}
