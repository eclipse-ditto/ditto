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
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonValue;

/**
 * Links is a container for multiple {@link BaseLink}s.
 *
 * @since 2.4.0
 */
public interface Links extends Iterable<BaseLink<?>>, Jsonifiable<JsonArray> {

    static Links fromJson(final JsonArray jsonArray) {
        final List<BaseLink<?>> baseLinks = jsonArray.stream()
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .map(BaseLink::fromJson)
                .collect(Collectors.toList());
        return of(baseLinks);
    }

    static Links of(final Collection<BaseLink<?>> links) {
        return new ImmutableLinks(links);
    }

    default Stream<BaseLink<?>> stream() {
        return StreamSupport.stream(spliterator(), false);
    }
}
