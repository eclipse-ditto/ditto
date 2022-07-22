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
 * MultipleHreflang is a container for multiple {@link SingleHreflang}s.
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#bib-bcp47">BCP47 - Tags for Identifying Languages</a>
 * @since 3.0.0
 */
public interface MultipleHreflang extends Hreflang, Iterable<SingleHreflang>, Jsonifiable<JsonArray> {

    static MultipleHreflang fromJson(final JsonArray jsonArray) {
        final List<SingleHreflang> singleSecurities = jsonArray.stream()
                .filter(JsonValue::isString)
                .map(JsonValue::asString)
                .map(SingleHreflang::of)
                .collect(Collectors.toList());
        return of(singleSecurities);
    }

    static MultipleHreflang of(final Collection<SingleHreflang> hreflangs) {
        return new ImmutableMultipleHreflang(hreflangs);
    }

    default Stream<SingleHreflang> stream() {
        return StreamSupport.stream(spliterator(), false);
    }
}
