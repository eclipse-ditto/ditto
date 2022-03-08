/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.wot.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonValue;

/**
 * Immutable implementation of {@link MultipleOAuth2Scopes}.
 */
@Immutable
final class ImmutableMultipleOAuth2Scopes implements MultipleOAuth2Scopes {

    private final List<SingleOAuth2Scopes> scopes;

    ImmutableMultipleOAuth2Scopes(final Collection<SingleOAuth2Scopes> scopes) {
        this.scopes = Collections.unmodifiableList(new ArrayList<>(scopes));
    }

    @Override
    public Iterator<SingleOAuth2Scopes> iterator() {
        return scopes.iterator();
    }

    @Override
    public JsonArray toJson() {
        return scopes.stream()
                .map(SingleOAuth2Scopes::of)
                .map(JsonValue::of)
                .collect(JsonCollectors.valuesToArray());
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableMultipleOAuth2Scopes that = (ImmutableMultipleOAuth2Scopes) o;
        return Objects.equals(scopes, that.scopes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scopes);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" +
                "scopes=" + scopes +
                "]";
    }
}
