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
 * TmRequired holds which parts in a {@link ThingModel} are mandatory to be implemented in a {@link ThingDescription}
 * derived from the Thing Model.
 *
 * @see <a href="https://w3c.github.io/wot-thing-description/#thing-model-td-required">WoT TD Thing Model Required</a>
 * @since 2.4.0
 */
public interface TmRequired extends Iterable<TmRequiredElement>, Jsonifiable<JsonArray> {

    static TmRequired fromJson(final JsonArray jsonArray) {
        final List<TmRequiredElement> requiredElements = jsonArray.stream()
                .filter(JsonValue::isString)
                .map(JsonValue::asString)
                .map(TmRequiredElement::of)
                .collect(Collectors.toList());
        return of(requiredElements);
    }

    static TmRequired of(final Collection<TmRequiredElement> requiredElements) {
        return new ImmutableTmRequired(requiredElements);
    }

    default Stream<TmRequiredElement> stream() {
        return StreamSupport.stream(spliterator(), false);
    }
}
