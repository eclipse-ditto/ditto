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
package org.eclipse.ditto.things.model.signals.events;

import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.base.model.signals.events.EventsourcedEvent;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.things.model.FeatureDefinition;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link FeatureDefinitionModified}.
 */
public final class FeatureDefinitionModifiedTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(Event.JsonFields.TIMESTAMP, TestConstants.TIMESTAMP.toString())
            .set(Event.JsonFields.TYPE, FeatureDefinitionModified.TYPE)
            .set(Event.JsonFields.METADATA, TestConstants.METADATA.toJson())
            .set(EventsourcedEvent.JsonFields.REVISION, TestConstants.Thing.REVISION_NUMBER)
            .set(ThingEvent.JsonFields.THING_ID, TestConstants.Thing.THING_ID.toString())
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
                .usingGetClass()
                .verify();
    }

    @Test
    public void tryToCreateInstanceWithNullThingId() {
        assertThatNullPointerException()
                .isThrownBy(() -> FeatureDefinitionModified.of(null, TestConstants.Feature.FLUX_CAPACITOR_ID,
                        TestConstants.Feature.FLUX_CAPACITOR_DEFINITION, TestConstants.Thing.REVISION_NUMBER,
                        TestConstants.TIMESTAMP, TestConstants.EMPTY_DITTO_HEADERS, TestConstants.METADATA))
                .withMessage("The %s must not be null!", "entityId")
                .withNoCause();
    }

    @Test
    public void tryToCreateInstanceWithNullFeatureId() {
        assertThatNullPointerException()
                .isThrownBy(() -> FeatureDefinitionModified.of(TestConstants.Thing.THING_ID, null,
                        TestConstants.Feature.FLUX_CAPACITOR_DEFINITION, TestConstants.Thing.REVISION_NUMBER,
                        TestConstants.TIMESTAMP, TestConstants.EMPTY_DITTO_HEADERS, TestConstants.METADATA))
                .withMessage("The %s must not be null!", "Feature ID")
                .withNoCause();
    }

    @Test
    public void tryToCreateInstanceWithNullDefinition() {
        assertThatNullPointerException()
                .isThrownBy(() -> FeatureDefinitionModified.of(TestConstants.Thing.THING_ID,
                        TestConstants.Feature.FLUX_CAPACITOR_ID, null, TestConstants.Thing.REVISION_NUMBER,
                        TestConstants.TIMESTAMP, TestConstants.EMPTY_DITTO_HEADERS, TestConstants.METADATA))
                .withMessage("The %s must not be null!", "Feature Definition")
                .withNoCause();
    }

    @Test
    public void toJsonReturnsExpected() {
        final FeatureDefinitionModified underTest =
                FeatureDefinitionModified.of(TestConstants.Thing.THING_ID, TestConstants.Feature.FLUX_CAPACITOR_ID,
                        TestConstants.Feature.FLUX_CAPACITOR_DEFINITION, TestConstants.Thing.REVISION_NUMBER,
                        TestConstants.TIMESTAMP, TestConstants.EMPTY_DITTO_HEADERS,
                        TestConstants.METADATA);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualToIgnoringFieldDefinitions(KNOWN_JSON);
    }

    @Test
    public void createInstanceFromValidJson() {
        final FeatureDefinitionModified underTest =
                FeatureDefinitionModified.fromJson(KNOWN_JSON.toString(), TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat((CharSequence) underTest.getEntityId()).isEqualTo(TestConstants.Thing.THING_ID);
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
                        TestConstants.EMPTY_DITTO_HEADERS,
                        TestConstants.METADATA);

        assertThat(underTest.getResourcePath()).isEqualTo(expectedResourcePath);
    }

}
