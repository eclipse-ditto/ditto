/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.model.signals.commands.modify;

import static org.eclipse.ditto.things.model.signals.commands.assertions.ThingCommandAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonKeyInvalidException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.json.assertions.DittoJsonAssertions;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.TestConstants;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandResponse;
import org.eclipse.ditto.things.model.signals.commands.exceptions.AttributePointerInvalidException;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ModifyAttributeResponse}.
 */
public final class ModifyAttributeResponseTest {

    private static final JsonObject KNOWN_JSON_CREATED = JsonFactory.newObjectBuilder()
            .set(ThingCommandResponse.JsonFields.TYPE, ModifyAttributeResponse.TYPE)
            .set(ThingCommandResponse.JsonFields.STATUS, HttpStatus.CREATED.getCode())
            .set(ThingCommandResponse.JsonFields.JSON_THING_ID, TestConstants.Thing.THING_ID.toString())
            .set(ModifyAttributeResponse.JSON_ATTRIBUTE, TestConstants.Thing.LOCATION_ATTRIBUTE_POINTER.toString())
            .set(ModifyAttributeResponse.JSON_VALUE, TestConstants.Thing.LOCATION_ATTRIBUTE_VALUE)
            .build();

    private static final JsonObject KNOWN_JSON_UPDATED = JsonFactory.newObjectBuilder()
            .set(ThingCommandResponse.JsonFields.TYPE, ModifyAttributeResponse.TYPE)
            .set(ThingCommandResponse.JsonFields.STATUS, HttpStatus.NO_CONTENT.getCode())
            .set(ThingCommandResponse.JsonFields.JSON_THING_ID, TestConstants.Thing.THING_ID.toString())
            .set(ModifyAttributeResponse.JSON_ATTRIBUTE, TestConstants.Thing.LOCATION_ATTRIBUTE_POINTER.toString())
            .build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(ModifyAttributeResponse.class,
                areImmutable(),
                provided(JsonValue.class, JsonPointer.class, ThingId.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ModifyAttributeResponse.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test
    public void toJsonReturnsExpected() {
        final ModifyAttributeResponse underTestCreated = ModifyAttributeResponse
                .created(TestConstants.Thing.THING_ID, TestConstants.Thing.LOCATION_ATTRIBUTE_POINTER,
                        TestConstants.Thing.LOCATION_ATTRIBUTE_VALUE,
                        TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJsonCreated = underTestCreated.toJson(FieldType.regularOrSpecial());

        DittoJsonAssertions.assertThat(actualJsonCreated).isEqualTo(KNOWN_JSON_CREATED);

        final ModifyAttributeResponse underTestUpdated =
                ModifyAttributeResponse.modified(TestConstants.Thing.THING_ID,
                        TestConstants.Thing.LOCATION_ATTRIBUTE_POINTER,
                        TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJsonUpdated = underTestUpdated.toJson(FieldType.regularOrSpecial());

        DittoJsonAssertions.assertThat(actualJsonUpdated).isEqualTo(KNOWN_JSON_UPDATED);
    }

    @Test
    public void createInstanceFromValidJson() {
        final ModifyAttributeResponse underTestCreated =
                ModifyAttributeResponse.fromJson(KNOWN_JSON_CREATED, TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTestCreated).isNotNull();
        assertThat(underTestCreated.getAttributeValue()).hasValue(TestConstants.Thing.LOCATION_ATTRIBUTE_VALUE);

        final ModifyAttributeResponse underTestUpdated =
                ModifyAttributeResponse.fromJson(KNOWN_JSON_UPDATED, TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTestUpdated).isNotNull();
        assertThat(underTestUpdated.getAttributeValue()).isEmpty();
    }

    @Test(expected = JsonKeyInvalidException.class)
    public void tryToCreateInstanceWithValidArguments() {
        ModifyAttributeResponse.modified(TestConstants.Thing.THING_ID, TestConstants.Pointer.INVALID_JSON_POINTER,
                TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test(expected = AttributePointerInvalidException.class)
    public void createInstanceWithEmptyPointer() {
        ModifyAttributeResponse.modified(TestConstants.Thing.THING_ID, TestConstants.Pointer.EMPTY_JSON_POINTER,
                TestConstants.EMPTY_DITTO_HEADERS);
    }

}
