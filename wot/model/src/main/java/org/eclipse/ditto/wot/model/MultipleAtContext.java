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

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonArray;

/**
 * MultipleAtContext is a container for multiple {@link SingleAtContext}s.
 *
 * @since 2.4.0
 */
public interface MultipleAtContext extends AtContext, Iterable<SingleAtContext>, Jsonifiable<JsonArray> {

    static MultipleAtContext fromJson(final JsonArray jsonArray) {
        final List<SingleAtContext> singleDataSchemaTypes = jsonArray.stream()
                .flatMap(jsonValue -> {
                    if (jsonValue.isString()) {
                        return Stream.of(SingleUriAtContext.of(jsonValue.asString()));
                    }
                    if (jsonValue.isObject()) {
                        return jsonValue.asObject().stream()
                                .map(field -> SinglePrefixedAtContext.of(field.getKeyName(),
                                        SingleUriAtContext.of(field.getValue().asString()))
                                );
                    }
                    return Stream.empty();
                })
                .collect(Collectors.toList());
        return of(singleDataSchemaTypes);
    }

    static MultipleAtContext of(final Collection<SingleAtContext> contexts) {
        return new ImmutableMultipleAtContext(contexts);
    }

    default Stream<SingleAtContext> stream() {
        return StreamSupport.stream(spliterator(), false);
    }

    /**
     * Determines the {@code @context} prefix of the passed IRI in {@code singleUriAtContext}.
     * If this {@code MultipleAtContext} contains such an IRI, the prefix for that context entry is returned.
     *
     * @param singleUriAtContext the IRI to determine the context prefix for.
     * @return the determined context prefix if the IRI was part of this {@code MultipleAtContext} instance or an
     * empty optional instead.
     * @since 3.0.0
     */
    default Optional<String> determinePrefixForSingleUriAtContext(final SingleUriAtContext singleUriAtContext) {
        return stream()
                .filter(SinglePrefixedAtContext.class::isInstance)
                .map(SinglePrefixedAtContext.class::cast)
                .filter(singlePrefixedAtContext -> singlePrefixedAtContext
                        .getDelegateContext().equals(singleUriAtContext))
                .map(SinglePrefixedAtContext::getPrefix)
                .findFirst();
    }
}
