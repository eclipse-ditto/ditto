/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.model.signals.commands.query;

import static org.eclipse.ditto.things.model.signals.commands.assertions.ThingCommandAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonKeyInvalidException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.json.assertions.DittoJsonAssertions;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.TestConstants;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandResponse;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link RetrieveFeatureDesiredPropertyResponse}.
 */
public final class RetrieveFeatureDesiredPropertyResponseTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(ThingCommandResponse.JsonFields.TYPE, RetrieveFeatureDesiredPropertyResponse.TYPE)
            .set(ThingCommandResponse.JsonFields.STATUS, HttpStatus.OK.getCode())
            .set(ThingCommandResponse.JsonFields.JSON_THING_ID, TestConstants.Thing.THING_ID.toString())
            .set(RetrieveFeatureDesiredPropertiesResponse.JSON_FEATURE_ID, TestConstants.Feature.HOVER_BOARD_ID)
            .set(RetrieveFeatureDesiredPropertyResponse.JSON_DESIRED_PROPERTY,
                    TestConstants.Feature.HOVER_BOARD_DESIRED_PROPERTY_POINTER.toString())
            .set(RetrieveFeatureDesiredPropertyResponse.JSON_DESIRED_VALUE,
                    TestConstants.Feature.HOVER_BOARD_DESIRED_PROPERTY_VALUE)
            .build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(RetrieveFeatureDesiredPropertyResponse.class,
                areImmutable(),
                provided(JsonPointer.class, JsonValue.class, ThingId.class).areAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(RetrieveFeatureDesiredPropertyResponse.class)
                .withRedefinedSuperclass()
                .usingGetClass()
                .verify();
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullFeatureProperty() {
        RetrieveFeatureDesiredPropertyResponse.of(TestConstants.Thing.THING_ID,
                TestConstants.Feature.HOVER_BOARD_ID,
                TestConstants.Feature.HOVER_BOARD_DESIRED_PROPERTY_POINTER,
                null,
                TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test
    public void toJsonReturnsExpected() {
        final RetrieveFeatureDesiredPropertyResponse underTest =
                RetrieveFeatureDesiredPropertyResponse.of(TestConstants.Thing.THING_ID,
                        TestConstants.Feature.HOVER_BOARD_ID,
                        TestConstants.Feature.HOVER_BOARD_DESIRED_PROPERTY_POINTER,
                        TestConstants.Feature.HOVER_BOARD_DESIRED_PROPERTY_VALUE,
                        TestConstants.EMPTY_DITTO_HEADERS);

        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        DittoJsonAssertions.assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void createInstanceFromValidJson() {
        final RetrieveFeatureDesiredPropertyResponse underTest = RetrieveFeatureDesiredPropertyResponse.fromJson(
                KNOWN_JSON.toString(),
                TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getDesiredPropertyValue())
                .isEqualTo(TestConstants.Feature.HOVER_BOARD_DESIRED_PROPERTY_VALUE);
    }

    @Test(expected = JsonKeyInvalidException.class)
    public void tryToCreateInstanceWithInvalidArguments() {
        RetrieveFeatureDesiredPropertyResponse.of(TestConstants.Thing.THING_ID,
                TestConstants.Feature.HOVER_BOARD_ID,
                TestConstants.Pointer.INVALID_JSON_POINTER,
                TestConstants.Feature.HOVER_BOARD_DESIRED_PROPERTY_VALUE,
                TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test
    public void createInstanceFromInvalidJson() {
        final JsonObject invalidJson = KNOWN_JSON.toBuilder()
                .set(RetrieveFeatureDesiredProperty.JSON_DESIRED_PROPERTY_POINTER,
                        TestConstants.Pointer.INVALID_JSON_POINTER.toString())
                .build();

        Assertions.assertThatExceptionOfType(JsonParseException.class)
                .isThrownBy(() -> RetrieveFeatureDesiredPropertyResponse.fromJson(invalidJson, TestConstants.EMPTY_DITTO_HEADERS))
                .withMessage("Failed to deserialize JSON object to a command response of type <%s>:" +
                        " The JSON key <äöü> is invalid!", RetrieveFeatureDesiredPropertyResponse.TYPE)
                .withCauseInstanceOf(JsonKeyInvalidException.class);
    }

}
