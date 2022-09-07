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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.junit.Test;

/**
 * Unit test for {@link EntityTypeJsonDeserializer}.
 */
public final class EntityTypeJsonDeserializerTest {

    private static final JsonFieldDefinition<String> FIELD_DEFINITION = JsonFieldDefinition.ofString("entityType");

    @Test
    public void assertImmutability() {
        assertInstancesOf(EntityTypeJsonDeserializer.class, areImmutable());
    }

    @Test
    public void deserializeEntityTypeFromValidArgumentsReturnsExpected() {
        final EntityType entityType = EntityType.of("thud");
        final JsonObject jsonObject = JsonObject.newBuilder()
                .set(FIELD_DEFINITION, entityType.toString())
                .build();

        final EntityType deserializedEntityType =
                EntityTypeJsonDeserializer.deserializeEntityType(jsonObject, FIELD_DEFINITION);

        assertThat((CharSequence) deserializedEntityType).isEqualTo(entityType);
    }

    @Test
    public void deserializeEntityTypeFromEmptyJsonObjectFails() {
        Assertions.assertThatExceptionOfType(JsonMissingFieldException.class)
                .isThrownBy(() -> EntityTypeJsonDeserializer.deserializeEntityType(JsonObject.empty(),
                        FIELD_DEFINITION))
                .withNoCause();
    }

    @Test
    public void deserializeEntityTypeFromJsonWithUnexpectedValueAtDefinedPathFails() {
        final boolean unexpectedValue = false;
        final JsonObject jsonObject = JsonObject.newBuilder()
                .set(FIELD_DEFINITION.getPointer(), unexpectedValue)
                .build();

        Assertions.assertThatExceptionOfType(JsonParseException.class)
                .isThrownBy(() -> EntityTypeJsonDeserializer.deserializeEntityType(jsonObject,
                        FIELD_DEFINITION))
                .withMessage("Value <%s> for <%s> is not of type <String>!",
                        unexpectedValue,
                        FIELD_DEFINITION.getPointer())
                .withNoCause();
    }

    @Test
    public void deserializeEntityTypeFromJsonWithInvalidEntityTypeFails() {
        final JsonObject jsonObject = JsonObject.newBuilder()
                .set(FIELD_DEFINITION, "")
                .build();

        Assertions.assertThatExceptionOfType(JsonParseException.class)
                .isThrownBy(() -> EntityTypeJsonDeserializer.deserializeEntityType(jsonObject,
                        FIELD_DEFINITION ))
                .withMessage("Failed to deserialize value of key <%s> as %s: The argument 'value' must not be empty!",
                        FIELD_DEFINITION.getPointer(),
                        EntityType.class.getName())
                .withCauseInstanceOf(IllegalArgumentException.class);
    }

}