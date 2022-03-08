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

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonObject;

/**
 * Titles "provide multi-language human-readable titles (e.g., display a text for UI representation in different
 * languages)."
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#multilanguage">WoT TD Multilanguage</a>
 * @since 2.4.0
 */
public interface Titles extends Map<Locale, Title>, Jsonifiable<JsonObject> {

    static Titles fromJson(final JsonObject jsonObject) {
        return of(jsonObject.stream().collect(Collectors.toMap(
                field -> new Locale(field.getKey().toString()),
                field -> Title.of(field.getValue().asString()),
                (u, v) -> {
                    throw new IllegalStateException(String.format("Duplicate key %s", u));
                },
                LinkedHashMap::new)
        ));
    }

    static Titles of(final Map<Locale, Title> titles) {
        return new ImmutableTitles(titles);
    }
}
