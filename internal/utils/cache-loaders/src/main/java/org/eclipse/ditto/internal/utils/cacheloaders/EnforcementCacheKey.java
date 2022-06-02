/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.internal.utils.cache.CacheKey;

/**
 * Implementation for a {@link CacheKey} used in scope of policy enforcement.
 */
@Immutable
public final class EnforcementCacheKey implements CacheKey<EnforcementContext> {

    static final String DELIMITER = ":";

    private final EntityId entityId;
    @Nullable private final EnforcementContext context;

    EnforcementCacheKey(final EntityId entityId, @Nullable final EnforcementContext context) {
        this.entityId = entityId;
        this.context = context;
    }


    /**
     * Deserialize entity ID with resource type from a string.
     *
     * @param string the string.
     * @return the entity ID with resource type.
     * @throws IllegalArgumentException if the string does not have the expected format.
     */
    public static EnforcementCacheKey readFrom(final String string) {
        checkNotNull(string, "string");

        final int delimiterIndex = string.indexOf(DELIMITER);
        if (delimiterIndex < 0) {
            final String message = MessageFormat.format("Unexpected EntityId format: <{0}>", string);
            throw new IllegalArgumentException(message);
        } else {
            final EntityType entityType = EntityType.of(string.substring(0, delimiterIndex));
            final EntityId id = EntityId.of(entityType, string.substring(delimiterIndex + 1));
            return new EnforcementCacheKey(id, null);
        }
    }

    public static EnforcementCacheKey of(final EntityId entityId) {
        return new EnforcementCacheKey(entityId, null);
    }

    public static EnforcementCacheKey of(final EntityId entityId, final EnforcementContext context) {
        return new EnforcementCacheKey(entityId, context);
    }

    @Override
    public EntityId getId() {
        return entityId;
    }

    @Override
    public Optional<EnforcementContext> getCacheLookupContext() {
        return Optional.ofNullable(context);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final EnforcementCacheKey that = (EnforcementCacheKey) o;
        return isIdEqualValueBased(that);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityId);
    }

    @Override
    public String toString() {
        // cache context is not in the string representation
        // because it is used for serialization and cache context is local
        return String.format("%s%s%s", entityId.getEntityType(), DELIMITER, entityId);
    }

    // this allows working with fallback entity IDs as well without breaking caching
    private boolean isIdEqualValueBased(final EnforcementCacheKey that) {
        return Objects.equals(entityId.getEntityType(), that.entityId.getEntityType()) &&
                Objects.equals(entityId.toString(), that.entityId.toString());
    }

}
