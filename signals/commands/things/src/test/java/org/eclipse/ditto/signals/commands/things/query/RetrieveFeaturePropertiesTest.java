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
package org.eclipse.ditto.signals.commands.things.query;

import static org.eclipse.ditto.signals.commands.things.assertions.ThingCommandAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseOptions;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.model.things.ThingIdInvalidException;
import org.eclipse.ditto.signals.commands.things.TestConstants;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link RetrieveFeatureProperties}.
 */
public final class RetrieveFeaturePropertiesTest {

    private static final String SELECTED_FIELDS = "field1,field2,field3";

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(ThingCommand.JsonFields.TYPE, RetrieveFeatureProperties.TYPE)
            .set(ThingCommand.JsonFields.JSON_THING_ID, TestConstants.Thing.THING_ID.toString())
            .set(RetrieveFeatureProperties.JSON_FEATURE_ID, TestConstants.Feature.FLUX_CAPACITOR_ID)
            .build();

    private static final JsonObject KNOWN_JSON_WITH_FIELD_SELECTION = JsonFactory.newObjectBuilder()
            .set(ThingCommand.JsonFields.TYPE, RetrieveFeatureProperties.TYPE)
            .set(ThingCommand.JsonFields.JSON_THING_ID, TestConstants.Thing.THING_ID.toString())
            .set(RetrieveFeatureProperties.JSON_FEATURE_ID, TestConstants.Feature.FLUX_CAPACITOR_ID)
            .set(RetrieveFeatureProperties.JSON_SELECTED_FIELDS, SELECTED_FIELDS)
            .build();

    private static final JsonParseOptions JSON_PARSE_OPTIONS =
            JsonFactory.newParseOptionsBuilder().withoutUrlDecoding().build();


    @Test
    public void assertImmutability() {
        assertInstancesOf(RetrieveFeatureProperties.class,
                areImmutable(),
                provided(JsonFieldSelector.class, ThingId.class).isAlsoImmutable());
    }


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(RetrieveFeatureProperties.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test(expected = ThingIdInvalidException.class)
    public void tryToCreateInstanceWithNullThingIdString() {
        RetrieveFeatureProperties.of((String) null, TestConstants.Feature.FLUX_CAPACITOR_ID,
                TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullThingId() {
        RetrieveFeatureProperties.of((ThingId) null, TestConstants.Feature.FLUX_CAPACITOR_ID,
                TestConstants.EMPTY_DITTO_HEADERS);
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullFeatureId() {
        RetrieveFeatureProperties.of(TestConstants.Thing.THING_ID, null, TestConstants.EMPTY_DITTO_HEADERS);
    }


    @Test
    public void tryToCreateInstanceWithValidArguments() {
        RetrieveFeatureProperties.of(TestConstants.Thing.THING_ID, TestConstants.Feature.FLUX_CAPACITOR_ID,
                TestConstants.EMPTY_DITTO_HEADERS);
    }


    @Test
    public void toJsonReturnsExpected() {
        final RetrieveFeatureProperties underTest =
                RetrieveFeatureProperties.of(TestConstants.Thing.THING_ID, TestConstants.Feature.FLUX_CAPACITOR_ID,
                        TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }


    @Test
    public void jsonSerializationWorksAsExpectedWithSelectedFields() {
        final RetrieveFeatureProperties underTest =
                RetrieveFeatureProperties.of(TestConstants.Thing.THING_ID, TestConstants.Feature.FLUX_CAPACITOR_ID,
                        JsonFactory.newFieldSelector(SELECTED_FIELDS, JSON_PARSE_OPTIONS),
                        TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON_WITH_FIELD_SELECTION);
    }


    @Test
    public void createInstanceFromValidJson() {
        final RetrieveFeatureProperties underTest =
                RetrieveFeatureProperties.fromJson(KNOWN_JSON.toString(), TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat((CharSequence) underTest.getEntityId()).isEqualTo(TestConstants.Thing.THING_ID);
        assertThat(underTest.getFeatureId()).isEqualTo(TestConstants.Feature.FLUX_CAPACITOR_ID);
    }


    @Test
    public void createInstanceFromValidJsonWithSelectedFields() {
        final RetrieveFeatureProperties underTest = RetrieveFeatureProperties
                .fromJson(KNOWN_JSON_WITH_FIELD_SELECTION.toString(), TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat((CharSequence) underTest.getEntityId()).isEqualTo(TestConstants.Thing.THING_ID);
        assertThat(underTest.getSelectedFields())
                .contains(JsonFactory.newFieldSelector(SELECTED_FIELDS, JSON_PARSE_OPTIONS));
    }

}
