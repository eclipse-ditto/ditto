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
package org.eclipse.ditto.internal.utils.cache;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.type.EntityType;

/**
 * Implementation of {@code EntityIdWithResourceType}.
 */
@Immutable
final class ImmutableCacheKey implements CacheKey {

    static final String DELIMITER = ":";

    private final EntityId id;
    @Nullable private final CacheLookupContext cacheLookupContext;

    private ImmutableCacheKey(final EntityId id, @Nullable final CacheLookupContext cacheLookupContext) {
        this.id = checkNotNull(id, "id");
        this.cacheLookupContext = cacheLookupContext;
    }

    /**
     * Create a new entity ID from the given  {@code resourceType} and {@code id}.
     *
     * @param id the entity ID.
     * @return the entity ID with resource type object.
     * @throws NullPointerException if {@code id} is {@code null}.
     */
    static CacheKey of(final EntityId id) {
        return new ImmutableCacheKey(id, null);
    }

    /**
     * Create a new entity ID from the given  {@code resourceType} and {@code id}.
     *
     * @param id the entity ID.
     * @param cacheLookupContext additional context information to use for the cache lookup.
     * @return the entity ID with resource type object.
     * @throws NullPointerException if {@code id} is {@code null}.
     */
    static CacheKey of(final EntityId id, final CacheLookupContext cacheLookupContext) {
        return new ImmutableCacheKey(id, cacheLookupContext);
    }

    /**
     * Deserialize entity ID with resource type from a string.
     *
     * @param string the string.
     * @return the entity ID with resource type.
     * @throws IllegalArgumentException if the string does not have the expected format.
     */
    static CacheKey readFrom(final String string) {
        checkNotNull(string, "string");

        final int delimiterIndex = string.indexOf(DELIMITER);
        if (delimiterIndex < 0) {
            final String message = MessageFormat.format("Unexpected EntityId format: <{0}>", string);
            throw new IllegalArgumentException(message);
        } else {
            final EntityType entityType = EntityType.of(string.substring(0, delimiterIndex));
            final EntityId id = EntityId.of(entityType, string.substring(delimiterIndex + 1));
            return new ImmutableCacheKey(id, null);
        }
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
    public boolean equals(@Nullable final Object o) {
        if (o instanceof ImmutableCacheKey) {
            final var that = (ImmutableCacheKey) o;
            return isIdEqualValueBased(that) && Objects.equals(cacheLookupContext, that.cacheLookupContext);
        } else {
            return false;
        }
    }

    // this allows working with fallback entity IDs as well without breaking caching
    private boolean isIdEqualValueBased(final ImmutableCacheKey that) {
        return Objects.equals(id.getEntityType(), that.id.getEntityType()) &&
                Objects.equals(id.toString(), that.id.toString());
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, cacheLookupContext);
    }

    @Override
    public String toString() {
        // cache context is not in the string representation
        // because it is used for serialization and cache context is local
        return String.format("%s%s%s", id.getEntityType(), DELIMITER, id);
    }

}
