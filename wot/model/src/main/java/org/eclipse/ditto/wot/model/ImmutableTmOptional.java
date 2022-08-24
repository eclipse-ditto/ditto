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
 * Immutable implementation of {@link TmOptional}.
 */
@Immutable
final class ImmutableTmOptional implements TmOptional {

    private final List<TmOptionalElement> optionalElements;

    ImmutableTmOptional(final Collection<TmOptionalElement> optionalElements) {
        this.optionalElements = Collections.unmodifiableList(new ArrayList<>(optionalElements));
    }

    @Override
    public Iterator<TmOptionalElement> iterator() {
        return optionalElements.iterator();
    }

    @Override
    public JsonArray toJson() {
        return optionalElements.stream()
                .map(TmOptionalElement::toString)
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
        final ImmutableTmOptional that = (ImmutableTmOptional) o;
        return Objects.equals(optionalElements, that.optionalElements);
    }

    @Override
    public int hashCode() {
        return Objects.hash(optionalElements);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" +
                "optionalElements=" + optionalElements +
                "]";
    }
}
