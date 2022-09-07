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
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonKeyInvalidException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.assertions.DittoJsonAssertions;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingIdInvalidException;
import org.eclipse.ditto.things.model.signals.commands.TestConstants;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link RetrieveFeatureDesiredProperty}.
 */
public final class RetrieveFeatureDesiredPropertyTest {

    private static final JsonPointer DESIRED_PROPERTY_JSON_POINTER =
            JsonFactory.newPointer("desiredProperties/foo");

    private static final JsonPointer INVALID_DESIRED_PROPERTY_JSON_POINTER =
            JsonFactory.newPointer("desiredProperties/bar/füü");

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(ThingCommand.JsonFields.TYPE, RetrieveFeatureDesiredProperty.TYPE)
            .set(ThingCommand.JsonFields.JSON_THING_ID, TestConstants.Thing.THING_ID.toString())
            .set(RetrieveFeatureDesiredProperty.JSON_FEATURE_ID, TestConstants.Feature.HOVER_BOARD_ID)
            .set(RetrieveFeatureDesiredProperty.JSON_DESIRED_PROPERTY_POINTER, DESIRED_PROPERTY_JSON_POINTER.toString())
            .build();


    @Test
    public void assertImmutability() {
        assertInstancesOf(RetrieveFeatureDesiredProperty.class,
                areImmutable(),
                provided(JsonPointer.class, JsonFieldSelector.class, ThingId.class).areAlsoImmutable());
    }


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(RetrieveFeatureDesiredProperty.class)
                .withRedefinedSuperclass()
                .verify();
    }


    @Test(expected = ThingIdInvalidException.class)
    public void tryToCreateInstanceWithNullThingId() {
        RetrieveFeatureDesiredProperty.of(ThingId.of(null), TestConstants.Feature.HOVER_BOARD_ID,
                DESIRED_PROPERTY_JSON_POINTER,
                TestConstants.EMPTY_DITTO_HEADERS);
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullFeatureId() {
        RetrieveFeatureDesiredProperty.of(TestConstants.Thing.THING_ID, null, DESIRED_PROPERTY_JSON_POINTER,
                TestConstants.EMPTY_DITTO_HEADERS);
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullJsonPointer() {
        RetrieveFeatureDesiredProperty.of(TestConstants.Thing.THING_ID,
                TestConstants.Feature.HOVER_BOARD_ID,
                null,
                TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test
    public void tryToCreateInstanceWithValidArguments() {
        RetrieveFeatureDesiredProperty.of(TestConstants.Thing.THING_ID, TestConstants.Feature.HOVER_BOARD_ID,
                DESIRED_PROPERTY_JSON_POINTER, TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test
    public void jsonSerializationWorksAsExpected() {
        final RetrieveFeatureDesiredProperty underTest =
                RetrieveFeatureDesiredProperty.of(TestConstants.Thing.THING_ID, TestConstants.Feature.HOVER_BOARD_ID,
                        DESIRED_PROPERTY_JSON_POINTER, TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        DittoJsonAssertions.assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void createInstanceFromValidJson() {
        final RetrieveFeatureDesiredProperty underTest =
                RetrieveFeatureDesiredProperty.fromJson(KNOWN_JSON.toString(), TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        Assertions.assertThat((CharSequence) underTest.getEntityId()).isEqualTo(TestConstants.Thing.THING_ID);
        assertThat(underTest.getFeatureId()).isEqualTo(TestConstants.Feature.HOVER_BOARD_ID);
    }

    @Test(expected = JsonKeyInvalidException.class)
    public void tryToCreateInstanceWithInvalidArguments() {
        RetrieveFeatureDesiredProperty.of(TestConstants.Thing.THING_ID, TestConstants.Feature.HOVER_BOARD_ID,
                INVALID_DESIRED_PROPERTY_JSON_POINTER, TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test(expected = JsonKeyInvalidException.class)
    public void createInstanceFromInvalidJson() {
        final JsonObject invalidJson = KNOWN_JSON.toBuilder()
                .set(RetrieveFeatureDesiredProperty.JSON_DESIRED_PROPERTY_POINTER,
                        INVALID_DESIRED_PROPERTY_JSON_POINTER.toString())
                .build();
        RetrieveFeatureDesiredProperty.fromJson(invalidJson, TestConstants.EMPTY_DITTO_HEADERS);
    }

}
