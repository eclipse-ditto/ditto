/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.model.things;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.eclipse.ditto.model.things.TestConstants.Thing.THING_V1;
import static org.eclipse.ditto.model.things.TestConstants.Thing.THING_V2;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.junit.Test;

/**
 * Tests conversion of an {@link ImmutableThing} to JSON by specifying a different version than the version with
 * which the Thing has been created.
 */
public final class ImmutableThingToJsonVersionMismatchTest {

    private static final JsonObject EMPTY_ACL_JSON = JsonFactory.newObject();

    @Test
    public void convertThingV1ToJsonWithDefaultFieldsV2() {
        final JsonObject jsonV1 = THING_V1.toJson(JsonSchemaVersion.V_1);
        final JsonObject expectedJsonV2 = jsonV1.toBuilder()
                .remove(Thing.JsonFields.ACL)
                .build();

        final JsonObject actualJsonV2 = THING_V1.toJson();

        assertJsonEquals(expectedJsonV2, actualJsonV2);
    }

    @Test
    public void convertThingV1ToJsonWithAllFieldsV2() {
        final JsonObject jsonV1WithAllFields = THING_V1.toJson(JsonSchemaVersion.V_1, FieldType.all());
        final JsonObject expectedJsonV2WithAllFields = jsonV1WithAllFields.toBuilder()
                .remove(Thing.JsonFields.ACL)
                .set(Thing.JsonFields.SCHEMA_VERSION, JsonSchemaVersion.V_2.toInt())
                .build();

        final JsonObject actualJsonV2WithAllFields = THING_V1.toJson(FieldType.all());

        assertJsonEquals(expectedJsonV2WithAllFields, actualJsonV2WithAllFields);
    }

    @Test
    public void convertThingV2ToJsonWithDefaultFieldsV1() {
        final JsonObject jsonV2 = THING_V2.toJson();
        final JsonObject expectedJsonV1 = jsonV2.toBuilder()
                .set(Thing.JsonFields.ACL, EMPTY_ACL_JSON)
                .remove(Thing.JsonFields.POLICY_ID)
                .build();

        final JsonObject actualJsonV1 = THING_V2.toJson(JsonSchemaVersion.V_1);

        assertJsonEquals(expectedJsonV1, actualJsonV1);
    }

    @Test
    public void convertThingV2ToJsonWithAllFieldsV1() {
        final JsonObject jsonV2WithAllFields = THING_V2.toJson(FieldType.all());
        final JsonObject expectedJsonV1WithAllFields = jsonV2WithAllFields.toBuilder()
                .set(Thing.JsonFields.ACL, EMPTY_ACL_JSON)
                .remove(Thing.JsonFields.POLICY_ID)
                .set(Thing.JsonFields.SCHEMA_VERSION, JsonSchemaVersion.V_1.toInt())
                .build();

        final JsonObject actualJsonV1WithAllFields = THING_V2.toJson(JsonSchemaVersion.V_1, FieldType.all());

        assertJsonEquals(expectedJsonV1WithAllFields, actualJsonV1WithAllFields);
    }

    private static void assertJsonEquals(final JsonObject expected, final JsonObject actual) {
        assertThat(actual).isEqualToIgnoringFieldDefinitions(expected);
    }
}
