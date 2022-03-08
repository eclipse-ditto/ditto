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
 * FormElementAdditionalResponses is a container for {@link FormElementAdditionalResponse}s.
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#additionalexpectedresponse">WoT TD AdditionalExpectedResponse</a>
 * @since 2.4.0
 */
public interface FormElementAdditionalResponses
        extends Iterable<FormElementAdditionalResponse>, Jsonifiable<JsonArray> {

    static FormElementAdditionalResponses fromJson(final JsonArray jsonArray) {
        final List<FormElementAdditionalResponse> baseLinks = jsonArray.stream()
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .map(FormElementAdditionalResponse::fromJson)
                .collect(Collectors.toList());
        return of(baseLinks);
    }

    static FormElementAdditionalResponses of(final Collection<FormElementAdditionalResponse> links) {
        return new ImmutableFormElementAdditionalResponses(links);
    }

    default Stream<FormElementAdditionalResponse> stream() {
        return StreamSupport.stream(spliterator(), false);
    }

}
