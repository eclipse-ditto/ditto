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
package org.eclipse.ditto.internal.utils.cache;

import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.api.persistence.PersistenceLifecycle;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonFieldSelector;

/**
 * Immutable implementation of {@link CacheLookupContext}.
 */
@Immutable
final class ImmutableCacheLookupContext implements CacheLookupContext {

    @Nullable private final DittoHeaders dittoHeaders;
    @Nullable private final JsonFieldSelector jsonFieldSelector;
    @Nullable private final PersistenceLifecycle persistenceLifecycle;

    private ImmutableCacheLookupContext(@Nullable final DittoHeaders dittoHeaders,
            @Nullable final JsonFieldSelector jsonFieldSelector,
            @Nullable final PersistenceLifecycle persistenceLifecycle) {
        this.dittoHeaders = dittoHeaders;
        this.jsonFieldSelector = jsonFieldSelector;
        this.persistenceLifecycle = persistenceLifecycle;
    }

    /**
     * Creates a new CacheLookupContext from the passed optional {@code dittoHeaders} and {@code jsonFieldSelector}
     * retaining the for caching relevant {@code dittoHeaders} from the passed ones.
     *
     * @param dittoHeaders the DittoHeaders to use as key in the cache lookup context.
     * @param jsonFieldSelector the JsonFieldSelector to use in the cache lookup context.
     * @param persistenceLifecycle the persistence lifecycle of the looked up entity.
     * @return the created context.
     */
    static CacheLookupContext of(@Nullable final DittoHeaders dittoHeaders,
            @Nullable final JsonFieldSelector jsonFieldSelector,
            @Nullable final PersistenceLifecycle persistenceLifecycle) {

        return new ImmutableCacheLookupContext(dittoHeaders, jsonFieldSelector, persistenceLifecycle);
    }

    @Override
    public Optional<DittoHeaders> getDittoHeaders() {
        return Optional.ofNullable(dittoHeaders);
    }

    @Override
    public Optional<JsonFieldSelector> getJsonFieldSelector() {
        return Optional.ofNullable(jsonFieldSelector);
    }

    @Override
    public Optional<PersistenceLifecycle> getPersistenceLifecycle() {
        return Optional.ofNullable(persistenceLifecycle);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableCacheLookupContext that = (ImmutableCacheLookupContext) o;
        return Objects.equals(dittoHeaders, that.dittoHeaders) &&
                Objects.equals(jsonFieldSelector, that.jsonFieldSelector) &&
                Objects.equals(persistenceLifecycle, that.persistenceLifecycle);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dittoHeaders, jsonFieldSelector, persistenceLifecycle);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "dittoHeaders=" + dittoHeaders +
                ", jsonFieldSelector=" + jsonFieldSelector +
                ", persistenceLifecycle=" + persistenceLifecycle +
                "]";
    }
}
