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
 * Unit test for {@link EntityIdJsonDeserializer}.
 */
public final class EntityIdJsonDeserializerTest {

    private static final JsonFieldDefinition<String> FIELD_DEFINITION = JsonFieldDefinition.ofString("entityId");

    @Test
    public void assertImmutability() {
        assertInstancesOf(EntityIdJsonDeserializer.class, areImmutable());
    }

    @Test
    public void deserializeEntityIdFromValidArgumentsReturnsExpected() {
        final EntityIdForTests entityId = EntityIdForTests.of("grault");
        final JsonObject jsonObject = JsonObject.newBuilder()
                .set(FIELD_DEFINITION, entityId.toString())
                .build();

        final EntityId deserializedEntityId = EntityIdJsonDeserializer.deserializeEntityId(jsonObject,
                FIELD_DEFINITION,
                EntityIdForTests.ENTITY_TYPE);

        assertThat((CharSequence) deserializedEntityId).isEqualTo(entityId);
    }

    @Test
    public void deserializeEntityIdFromEmptyJsonObjectFails() {
        Assertions.assertThatExceptionOfType(JsonMissingFieldException.class)
                .isThrownBy(() -> EntityIdJsonDeserializer.deserializeEntityId(JsonObject.empty(),
                        FIELD_DEFINITION,
                        EntityIdForTests.ENTITY_TYPE))
                .withNoCause();
    }

    @Test
    public void deserializeEntityIdFromJsonWithUnexpectedValueAtDefinedPathFails() {
        final boolean unexpectedValue = false;
        final JsonObject jsonObject = JsonObject.newBuilder()
                .set(FIELD_DEFINITION.getPointer(), unexpectedValue)
                .build();

        Assertions.assertThatExceptionOfType(JsonParseException.class)
                .isThrownBy(() -> EntityIdJsonDeserializer.deserializeEntityId(jsonObject,
                        FIELD_DEFINITION,
                        EntityIdForTests.ENTITY_TYPE))
                .withMessage("Value <%s> for <%s> is not of type <String>!",
                        unexpectedValue,
                        FIELD_DEFINITION.getPointer())
                .withNoCause();
    }

    @Test
    public void deserializeEntityIdFromJsonWithInvalidEntityIdFails() {
        final JsonObject jsonObject = JsonObject.newBuilder()
                .set(FIELD_DEFINITION, EntityIdForTests.INVALID_ID)
                .build();

        Assertions.assertThatExceptionOfType(JsonParseException.class)
                .isThrownBy(() -> EntityIdJsonDeserializer.deserializeEntityId(jsonObject,
                        FIELD_DEFINITION,
                        EntityIdForTests.ENTITY_TYPE))
                .withMessage("Failed to deserialize value of key <%s> as %s: <%s> is invalid.",
                        FIELD_DEFINITION.getPointer(),
                        EntityId.class.getName(),
                        EntityIdForTests.INVALID_ID)
                .withCauseInstanceOf(EntityIdForTestsInvalidException.class);
    }

}