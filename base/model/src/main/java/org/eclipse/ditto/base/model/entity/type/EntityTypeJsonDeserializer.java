/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.entity.type;

import java.text.MessageFormat;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;

/**
 * This utility class provides functions to deserialize an {@link EntityType} from JSON.
 *
 * @since 2.1.0
 */
@Immutable
public final class EntityTypeJsonDeserializer {

    private EntityTypeJsonDeserializer() {
        throw new AssertionError();
    }

    /**
     * Deserializes an {@code EntityType} from the specified JSON object argument.
     *
     * @param jsonObject a JSON object that contains the string representation of an {@code EntityType} like defined by
     * {@code fieldDefinition}.
     * @param fieldDefinition defines the path within {@code jsonObject} to the string representation of the entity type
     * to be deserialized.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code jsonObject} does not contain a value at the
     * path defined by {@code fieldDefinition}.
     * @throws JsonParseException if
     * <ul>
     *   <li>the value at the position defined by {@code fieldDefinition} is not a string or</li>
     *   <li>the string representation of the entity type is invalid for an entity type of type {@code entityType}.</li>
     * </ul>
     */
    public static EntityType deserializeEntityType(final JsonObject jsonObject,
            final JsonFieldDefinition<String> fieldDefinition) {

        try {
            return EntityType.of(jsonObject.getValueOrThrow(fieldDefinition));
        } catch (final IllegalArgumentException e) {
            throw JsonParseException.newBuilder()
                    .message(MessageFormat.format("Failed to deserialize value of key <{0}> as {1}: {2}",
                            fieldDefinition.getPointer(),
                            EntityType.class.getName(),
                            e.getMessage()))
                    .cause(e)
                    .build();
        }
    }

}
