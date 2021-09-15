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

import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.cache.CacheLookupContext;
import org.eclipse.ditto.json.JsonFieldSelector;

/**
 * Immutable implementation of {@link org.eclipse.ditto.internal.utils.cache.CacheLookupContext}.
 */
@Immutable
final class SignalEnrichmentContext implements CacheLookupContext {

    @Nullable private final DittoHeaders dittoHeaders;
    @Nullable private final JsonFieldSelector jsonFieldSelector;

    private SignalEnrichmentContext(@Nullable final DittoHeaders dittoHeaders,
            @Nullable final JsonFieldSelector jsonFieldSelector) {
        this.dittoHeaders = dittoHeaders;
        this.jsonFieldSelector = jsonFieldSelector;
    }

    /**
     * Creates a new CacheLookupContext from the passed optional {@code dittoHeaders} and {@code jsonFieldSelector}
     * retaining the for caching relevant {@code dittoHeaders} from the passed ones.
     *
     * @param dittoHeaders the DittoHeaders to use as key in the cache lookup context.
     * @param jsonFieldSelector the JsonFieldSelector to use in the cache lookup context.
     * @return the created context.
     */
    static SignalEnrichmentContext of(@Nullable final DittoHeaders dittoHeaders,
            @Nullable final JsonFieldSelector jsonFieldSelector) {

        return new SignalEnrichmentContext(dittoHeaders, jsonFieldSelector);
    }

    /**
     * Returns the optional DittoHeaders this context provides.
     *
     * @return the optional DittoHeaders.
     */
    public Optional<DittoHeaders> getDittoHeaders() {
        return Optional.ofNullable(dittoHeaders);
    }


    /**
     * Returns the optional JsonFieldSelector this context provides.
     *
     * @return the optional JsonFieldSelector.
     */
    public Optional<JsonFieldSelector> getJsonFieldSelector() {
        return Optional.ofNullable(jsonFieldSelector);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final SignalEnrichmentContext that = (SignalEnrichmentContext) o;
        return Objects.equals(dittoHeaders, that.dittoHeaders) &&
                Objects.equals(jsonFieldSelector, that.jsonFieldSelector);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dittoHeaders, jsonFieldSelector);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "dittoHeaders=" + dittoHeaders +
                ", jsonFieldSelector=" + jsonFieldSelector +
                "]";
    }
}
