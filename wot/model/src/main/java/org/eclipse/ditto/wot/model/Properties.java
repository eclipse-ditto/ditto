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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonObject;

/**
 * Properties is a container for named {@link Property}s.
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#propertyaffordance">WoT TD PropertyAffordance</a>
 * @since 2.4.0
 */
public interface Properties extends Map<String, Property>, Jsonifiable<JsonObject> {

    static Properties fromJson(final JsonObject jsonObject) {
        return of(jsonObject.stream().collect(Collectors.toMap(
                field -> field.getKey().toString(),
                field -> Property.fromJson(field.getKey().toString(), field.getValue().asObject()),
                (u, v) -> {
                    throw new IllegalStateException(String.format("Duplicate key %s", u));
                },
                LinkedHashMap::new)
        ));
    }

    static Properties from(final Collection<Property> properties) {
        return of(properties.stream().collect(Collectors.toMap(
                Property::getPropertyName,
                p -> p,
                (u, v) -> {
                    throw new IllegalStateException(String.format("Duplicate key %s", u));
                },
                LinkedHashMap::new)
        ));
    }

    static Properties of(final Map<String, Property> properties) {
        return new ImmutableProperties(properties);
    }

    Optional<Property> getProperty(CharSequence propertyName);

}
