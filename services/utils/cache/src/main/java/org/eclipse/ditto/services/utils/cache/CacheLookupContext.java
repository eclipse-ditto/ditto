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
package org.eclipse.ditto.services.utils.cache;

import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.model.base.headers.DittoHeaders;

/**
 * TODO TJ extract as interface?
 */
@Immutable
public final class CacheLookupContext {

    @Nullable private final DittoHeaders dittoHeaders;
    @Nullable private final JsonFieldSelector jsonFieldSelector;

    public CacheLookupContext(@Nullable final DittoHeaders dittoHeaders,
            @Nullable final JsonFieldSelector jsonFieldSelector) {
        this.dittoHeaders = dittoHeaders;
        this.jsonFieldSelector = jsonFieldSelector;
    }

    public Optional<DittoHeaders> getDittoHeaders() {
        return Optional.ofNullable(dittoHeaders);
    }

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
        final CacheLookupContext that = (CacheLookupContext) o;
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
