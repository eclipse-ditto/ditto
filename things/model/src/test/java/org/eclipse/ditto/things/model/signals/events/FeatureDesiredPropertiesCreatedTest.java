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
package org.eclipse.ditto.things.model.signals.events;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.base.model.signals.events.EventsourcedEvent;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.things.model.FeatureProperties;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link FeatureDesiredPropertiesCreated}.
 */
public final class FeatureDesiredPropertiesCreatedTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(Event.JsonFields.TIMESTAMP, TestConstants.TIMESTAMP.toString())
            .set(Event.JsonFields.TYPE, FeatureDesiredPropertiesCreated.TYPE)
            .set(Event.JsonFields.METADATA, TestConstants.METADATA.toJson())
            .set(EventsourcedEvent.JsonFields.REVISION, TestConstants.Thing.REVISION_NUMBER)
            .set(ThingEvent.JsonFields.THING_ID, TestConstants.Thing.THING_ID.toString())
            .set(ThingEvent.JsonFields.FEATURE_ID, TestConstants.Feature.FLUX_CAPACITOR_ID)
            .set(FeatureDesiredPropertiesCreated.JSON_DESIRED_PROPERTIES,
                    TestConstants.Feature.FLUX_CAPACITOR_PROPERTIES.toJson(FieldType.regularOrSpecial()))
            .build();


    @Test
    public void assertImmutability() {
        assertInstancesOf(FeatureDesiredPropertiesCreated.class, areImmutable(),
                provided(FeatureProperties.class).isAlsoImmutable());
    }


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(FeatureDesiredPropertiesCreated.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullThingId() {
        FeatureDesiredPropertiesCreated.of(null, TestConstants.Feature.FLUX_CAPACITOR_ID,
                TestConstants.Feature.FLUX_CAPACITOR_PROPERTIES,
                TestConstants.Thing.REVISION_NUMBER, null, TestConstants.EMPTY_DITTO_HEADERS, null);
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullFeatureId() {
        FeatureDesiredPropertiesCreated.of(TestConstants.Thing.THING_ID, null, TestConstants.Feature.FLUX_CAPACITOR_PROPERTIES,
                TestConstants.Thing.REVISION_NUMBER, null, TestConstants.EMPTY_DITTO_HEADERS, null);
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullProperties() {
        FeatureDesiredPropertiesCreated.of(TestConstants.Thing.THING_ID, TestConstants.Feature.FLUX_CAPACITOR_ID, null,
                TestConstants.Thing.REVISION_NUMBER, null, TestConstants.EMPTY_DITTO_HEADERS, null);
    }


    @Test
    public void toJsonReturnsExpected() {
        final FeatureDesiredPropertiesCreated underTest =
                FeatureDesiredPropertiesCreated.of(TestConstants.Thing.THING_ID, TestConstants.Feature.FLUX_CAPACITOR_ID,
                        TestConstants.Feature.FLUX_CAPACITOR_PROPERTIES, TestConstants.Thing.REVISION_NUMBER,
                        TestConstants.TIMESTAMP,
                        TestConstants.EMPTY_DITTO_HEADERS,
                        TestConstants.METADATA);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }


    @Test
    public void createInstanceFromValidJson() {
        final FeatureDesiredPropertiesCreated underTest =
                FeatureDesiredPropertiesCreated.fromJson(KNOWN_JSON.toString(), TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat((CharSequence) underTest.getEntityId()).isEqualTo(TestConstants.Thing.THING_ID);
        assertThat(underTest.getFeatureId()).isEqualTo(TestConstants.Feature.FLUX_CAPACITOR_ID);
        assertThat(underTest.getDesiredProperties()).isEqualTo(TestConstants.Feature.FLUX_CAPACITOR_PROPERTIES);
    }

}
