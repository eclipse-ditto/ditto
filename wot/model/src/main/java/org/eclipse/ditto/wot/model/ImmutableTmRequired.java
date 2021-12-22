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
 * Immutable implementation of {@link TmRequired}.
 */
@Immutable
final class ImmutableTmRequired implements TmRequired {

    private final List<TmRequiredElement> requiredElements;

    ImmutableTmRequired(final Collection<TmRequiredElement> requiredElements) {
        this.requiredElements = Collections.unmodifiableList(new ArrayList<>(requiredElements));
    }

    @Override
    public Iterator<TmRequiredElement> iterator() {
        return requiredElements.iterator();
    }

    @Override
    public JsonArray toJson() {
        return requiredElements.stream()
                .map(TmRequiredElement::toString)
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
        final ImmutableTmRequired that = (ImmutableTmRequired) o;
        return Objects.equals(requiredElements, that.requiredElements);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requiredElements);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" +
                "requiredElements=" + requiredElements +
                "]";
    }
}
