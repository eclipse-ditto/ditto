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
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;

/**
 * Immutable implementation of {@link MultipleAtContext}.
 */
@Immutable
final class ImmutableMultipleAtContext implements MultipleAtContext {

    private final List<SingleAtContext> contexts;

    ImmutableMultipleAtContext(final Collection<SingleAtContext> contexts) {
        this.contexts = Collections.unmodifiableList(new ArrayList<>(contexts));
    }

    @Override
    public Iterator<SingleAtContext> iterator() {
        return contexts.iterator();
    }

    @Override
    public JsonArray toJson() {
        return contexts.stream()
                .map(singleAtContext -> {
                    if (singleAtContext instanceof SingleUriAtContext) {
                        return JsonValue.of(singleAtContext.toString());
                    } else if (singleAtContext instanceof SinglePrefixedAtContext) {
                        final SinglePrefixedAtContext singlePrefixedAtContext = (SinglePrefixedAtContext) singleAtContext;
                        return JsonObject.newBuilder()
                                .set(singlePrefixedAtContext.getPrefix(),
                                        singlePrefixedAtContext.getDelegateContext().toString())
                                .build();
                    } else {
                        throw new IllegalArgumentException("Unsupported @context: " +
                                singleAtContext.getClass().getSimpleName());
                    }
                }).reduce(JsonArray.empty(), (array, value) -> {
                    if (value.isObject()) {
                        final JsonArray newArray = ((JsonArray) array).stream()
                                .filter(v -> !v.isObject())
                                .collect(JsonCollectors.valuesToArray());
                        return newArray.add(((JsonArray) array)
                                .stream()
                                .filter(JsonValue::isObject)
                                .map(JsonValue::asObject)
                                .findFirst()
                                .map(object -> JsonFactory.mergeJsonValues(object, value.asObject()))
                                .orElse(value.asObject())
                        );
                    } else {
                        return ((JsonArray) array).add(value);
                    }
                }).asArray();
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableMultipleAtContext that = (ImmutableMultipleAtContext) o;
        return Objects.equals(contexts, that.contexts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contexts);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" +
                "contexts=" + contexts +
                "]";
    }
}
