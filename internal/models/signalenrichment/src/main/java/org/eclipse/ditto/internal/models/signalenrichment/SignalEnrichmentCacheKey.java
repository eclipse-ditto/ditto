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
package org.eclipse.ditto.internal.models.signalenrichment;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.internal.utils.cache.CacheKey;

/**
 * Implementation for a {@link CacheKey} used in scope of signal enrichment.
 */
@Immutable
final class SignalEnrichmentCacheKey implements CacheKey<SignalEnrichmentContext> {

    static final String DELIMITER = ":";

    private final EntityId id;
    @Nullable private final SignalEnrichmentContext context;

    private SignalEnrichmentCacheKey(final EntityId id, @Nullable final SignalEnrichmentContext context) {
        this.id = checkNotNull(id, "id");
        this.context = context;
    }

    /**
     * Create a new entity ID from the given  {@code resourceType} and {@code id}.
     *
     * @param id the entity ID.
     * @param cacheLookupContext additional context information to use for the cache lookup.
     * @return the entity ID with resource type object.
     * @throws NullPointerException if {@code id} is {@code null}.
     */
    static SignalEnrichmentCacheKey of(final EntityId id, @Nullable final SignalEnrichmentContext cacheLookupContext) {
        return new SignalEnrichmentCacheKey(id, cacheLookupContext);
    }

    @Override
    public EntityId getId() {
        return id;
    }

    @Override
    public Optional<SignalEnrichmentContext> getCacheLookupContext() {
        return Optional.ofNullable(context);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (o instanceof SignalEnrichmentCacheKey) {
            final var that = (SignalEnrichmentCacheKey) o;
            return isIdEqualValueBased(that) && Objects.equals(context, that.context);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, context);
    }

    @Override
    public String toString() {
        // cache context is not in the string representation
        // because it is used for serialization and cache context is local
        return String.format("%s%s%s", id.getEntityType(), DELIMITER, id);
    }

    // this allows working with fallback entity IDs as well without breaking caching
    private boolean isIdEqualValueBased(final SignalEnrichmentCacheKey that) {
        return Objects.equals(id.getEntityType(), that.id.getEntityType()) &&
                Objects.equals(id.toString(), that.id.toString());
    }

}
