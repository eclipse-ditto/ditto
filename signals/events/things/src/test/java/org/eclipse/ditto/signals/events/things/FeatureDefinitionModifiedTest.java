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
package org.eclipse.ditto.signals.events.things;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.things.FeatureDefinition;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.signals.events.base.Event;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link FeatureDefinitionModified}.
 */
public final class FeatureDefinitionModifiedTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(Event.JsonFields.TIMESTAMP, TestConstants.TIMESTAMP.toString())
            .set(Event.JsonFields.TYPE, FeatureDefinitionModified.TYPE)
            .set(Event.JsonFields.REVISION, TestConstants.Thing.REVISION_NUMBER)
            .set(ThingEvent.JsonFields.THING_ID, TestConstants.Thing.THING_ID)
            .set(ThingEvent.JsonFields.FEATURE_ID, TestConstants.Feature.FLUX_CAPACITOR_ID)
            .set(FeatureDefinitionModified.JSON_DEFINITION,
                    TestConstants.Feature.FLUX_CAPACITOR_DEFINITION.toJson())
            .build();


    @Test
    public void assertImmutability() {
        assertInstancesOf(FeatureDefinitionModified.class,
                areImmutable(),
                provided(FeatureDefinition.class).isAlsoImmutable());
    }


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(FeatureDefinitionModified.class)
                .withRedefinedSuperclass()
                .verify();
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullThingId() {
        FeatureDefinitionModified.of(null, TestConstants.Feature.FLUX_CAPACITOR_ID,
                TestConstants.Feature.FLUX_CAPACITOR_DEFINITION,
                TestConstants.Thing.REVISION_NUMBER, TestConstants.EMPTY_DITTO_HEADERS);
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullFeatureId() {
        FeatureDefinitionModified.of(TestConstants.Thing.THING_ID, null,
                TestConstants.Feature.FLUX_CAPACITOR_DEFINITION, TestConstants.Thing.REVISION_NUMBER,
                TestConstants.EMPTY_DITTO_HEADERS);
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullDefinition() {
        FeatureDefinitionModified.of(TestConstants.Thing.THING_ID, TestConstants.Feature.FLUX_CAPACITOR_ID, null,
                TestConstants.Thing.REVISION_NUMBER,
                TestConstants.EMPTY_DITTO_HEADERS);
    }


    @Test
    public void toJsonReturnsExpected() {
        final FeatureDefinitionModified underTest =
                FeatureDefinitionModified.of(TestConstants.Thing.THING_ID, TestConstants.Feature.FLUX_CAPACITOR_ID,
                        TestConstants.Feature.FLUX_CAPACITOR_DEFINITION,
                        TestConstants.Thing.REVISION_NUMBER, TestConstants.TIMESTAMP,
                        TestConstants.EMPTY_DITTO_HEADERS);
        final String actualJsonString = underTest.toJson(FieldType.regularOrSpecial()).toString();

        assertThat(actualJsonString).isEqualTo(KNOWN_JSON.toString());
    }


    @Test
    public void createInstanceFromValidJson() {
        final FeatureDefinitionModified underTest =
                FeatureDefinitionModified.fromJson(KNOWN_JSON.toString(), TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getThingId()).isEqualTo(TestConstants.Thing.THING_ID);
        assertThat(underTest.getFeatureId()).isEqualTo(TestConstants.Feature.FLUX_CAPACITOR_ID);
        assertThat(underTest.getDefinition()).isEqualTo(TestConstants.Feature.FLUX_CAPACITOR_DEFINITION);
    }


    @Test
    public void createInstanceFromJsonWithNullFeatureDefinition() {
        final JsonObject inputJson =
                KNOWN_JSON.setValue(FeatureDefinitionModified.JSON_DEFINITION.getPointer(), JsonFactory.nullLiteral());

        final FeatureDefinitionModified parsedEvent =
                FeatureDefinitionModified.fromJson(inputJson, DittoHeaders.empty());

        assertThat(parsedEvent.getDefinition()).isEqualTo(ThingsModelFactory.nullFeatureDefinition());
    }


    @Test
    public void getResourcePathReturnsExpected() {
        final JsonPointer expectedResourcePath =
                JsonFactory.newPointer("/features/" + TestConstants.Feature.FLUX_CAPACITOR_ID + "/definition");

        final FeatureDefinitionModified underTest =
                FeatureDefinitionModified.of(TestConstants.Thing.THING_ID, TestConstants.Feature.FLUX_CAPACITOR_ID,
                        TestConstants.Feature.FLUX_CAPACITOR_DEFINITION,
                        TestConstants.Thing.REVISION_NUMBER, TestConstants.TIMESTAMP,
                        TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest.getResourcePath()).isEqualTo(expectedResourcePath);
    }

}
