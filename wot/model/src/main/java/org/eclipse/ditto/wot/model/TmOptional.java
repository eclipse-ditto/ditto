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
 * TmOptional holds which parts in a {@link ThingModel} are optionally implemented in a {@link ThingDescription}
 * derived from the Thing Model.
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#thing-model-td-required">WoT TD Thing Model Optional</a>
 * @since 3.0.0
 */
public interface TmOptional extends Iterable<TmOptionalElement>, Jsonifiable<JsonArray> {

    static TmOptional fromJson(final JsonArray jsonArray) {
        final List<TmOptionalElement> optionalElements = jsonArray.stream()
                .filter(JsonValue::isString)
                .map(JsonValue::asString)
                .map(TmOptionalElement::of)
                .collect(Collectors.toList());
        return of(optionalElements);
    }

    static TmOptional of(final Collection<TmOptionalElement> optionalElements) {
        return new ImmutableTmOptional(optionalElements);
    }

    default Stream<TmOptionalElement> stream() {
        return StreamSupport.stream(spliterator(), false);
    }
}
