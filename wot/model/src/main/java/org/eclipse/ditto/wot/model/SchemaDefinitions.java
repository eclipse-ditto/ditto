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
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonObject;

/**
 * SchemaDefinitions is a container for named {@link SingleDataSchema}s.
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#dataschema">WoT TD DataSchema</a>
 * @since 2.4.0
 */
public interface SchemaDefinitions extends Map<String, SingleDataSchema>, Jsonifiable<JsonObject> {

    /**
     * Creates a SchemaDefinitions from the specified JSON object.
     *
     * @param jsonObject the JSON object mapping schema names to data schemas.
     * @return the SchemaDefinitions.
     */
    static SchemaDefinitions fromJson(final JsonObject jsonObject) {
        return of(jsonObject.stream().collect(Collectors.toMap(
                field -> field.getKey().toString(),
                field -> SingleDataSchema.fromJson(field.getValue().asObject()),
                (u, v) -> {
                    throw WotThingModelInvalidException.newBuilder(String.format("SingleDataSchemas: Duplicate key %s", u))
                            .build();
                },
                LinkedHashMap::new)
        ));
    }

    /**
     * Creates a SchemaDefinitions from the specified map of schema names to data schemas.
     *
     * @param dataSchemas the map of schema names to data schemas.
     * @return the SchemaDefinitions.
     */
    static SchemaDefinitions of(final Map<String, SingleDataSchema> dataSchemas) {
        return new ImmutableSchemaDefinitions(dataSchemas);
    }

    /**
     * Returns the schema definition with the specified name.
     *
     * @param key the name of the schema definition.
     * @return the optional schema.
     */
    Optional<SingleDataSchema> getSchemaDefinition(CharSequence key);

}
