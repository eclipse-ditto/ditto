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

    /**
     * Creates a new Properties container from the specified JSON object.
     *
     * @param jsonObject the JSON object containing property definitions.
     * @return the Properties container.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     */
    static Properties fromJson(final JsonObject jsonObject) {
        return of(jsonObject.stream().collect(Collectors.toMap(
                field -> field.getKey().toString(),
                field -> Property.fromJson(field.getKey().toString(), field.getValue().asObject()),
                (u, v) -> {
                    throw WotThingModelInvalidException.newBuilder(String.format("Properties: Duplicate key %s", u))
                            .build();
                },
                LinkedHashMap::new)
        ));
    }

    /**
     * Creates a new Properties container from the specified collection of properties.
     *
     * @param properties the collection of properties.
     * @return the Properties container.
     * @throws NullPointerException if {@code properties} is {@code null}.
     */
    static Properties from(final Collection<Property> properties) {
        return of(properties.stream().collect(Collectors.toMap(
                Property::getPropertyName,
                p -> p,
                (u, v) -> {
                    throw WotThingModelInvalidException.newBuilder(String.format("Properties: Duplicate key %s", u))
                            .build();
                },
                LinkedHashMap::new)
        ));
    }

    /**
     * Creates a new Properties container from the specified map of property name to property.
     *
     * @param properties the map of properties.
     * @return the Properties container.
     * @throws NullPointerException if {@code properties} is {@code null}.
     */
    static Properties of(final Map<String, Property> properties) {
        return new ImmutableProperties(properties);
    }

    /**
     * Returns the property with the specified name if it exists.
     *
     * @param propertyName the name of the property.
     * @return the optional property.
     */
    Optional<Property> getProperty(CharSequence propertyName);

}
