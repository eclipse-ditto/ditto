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
package org.eclipse.ditto.base.model.entity.id;

import java.text.MessageFormat;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;

/**
 * This utility class provides functions to deserialize an {@link EntityId} from JSON.
 *
 * @since 2.1.0
 */
@Immutable
public final class EntityIdJsonDeserializer {

    private EntityIdJsonDeserializer() {
        throw new AssertionError();
    }

    /**
     * Deserializes an {@code EntityId} from the specified JSON object argument.
     *
     * @param jsonObject a JSON object that contains the string representation of an {@code EntityId} like defined by
     * {@code fieldDefinition}.
     * @param fieldDefinition defines the path within {@code jsonObject} to the string representation of the entity ID
     * to be deserialized.
     * @param entityType the supposed type of the entity ID to be deserialized.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code jsonObject} does not contain a value at the
     * path defined by {@code fieldDefinition}.
     * @throws JsonParseException if
     * <ul>
     *   <li>the value at the position defined by {@code fieldDefinition} is not a string or</li>
     *   <li>the string representation of the entity ID is invalid for an entity ID of type {@code entityType}.</li>
     * </ul>
     */
    public static EntityId deserializeEntityId(final JsonObject jsonObject,
            final JsonFieldDefinition<String> fieldDefinition,
            final EntityType entityType) {

        ConditionChecker.checkNotNull(jsonObject, "jsonObject");
        ConditionChecker.checkNotNull(fieldDefinition, "fieldDefinition");
        ConditionChecker.checkNotNull(entityType, "entityType");

        try {
            return EntityId.of(entityType, jsonObject.getValueOrThrow(fieldDefinition));
        } catch (final EntityIdInvalidException e) {
            throw JsonParseException.newBuilder()
                    .message(MessageFormat.format("Failed to deserialize value of key <{0}> as {1}: {2}",
                            fieldDefinition.getPointer(),
                            EntityId.class.getName(),
                            e.getMessage()))
                    .cause(e)
                    .build();
        }
    }

}
