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
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link FeatureCreated}.
 */
public final class FeatureCreatedTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(Event.JsonFields.TIMESTAMP, TestConstants.TIMESTAMP.toString())
            .set(Event.JsonFields.TYPE, FeatureCreated.TYPE)
            .set(Event.JsonFields.METADATA, TestConstants.METADATA.toJson())
            .set(EventsourcedEvent.JsonFields.REVISION, TestConstants.Thing.REVISION_NUMBER)
            .set(ThingEvent.JsonFields.THING_ID, TestConstants.Thing.THING_ID.toString())
            .set(ThingEvent.JsonFields.FEATURE_ID, TestConstants.Feature.FLUX_CAPACITOR_ID)
            .set(FeatureCreated.JSON_FEATURE, TestConstants.Feature.FLUX_CAPACITOR.toJson(FieldType.regularOrSpecial()))
            .build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(FeatureCreated.class, areImmutable(),
                provided(Feature.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(FeatureCreated.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullThingId() {
        FeatureCreated.of((ThingId) null, TestConstants.Feature.FLUX_CAPACITOR, TestConstants.Thing.REVISION_NUMBER,
                TestConstants.TIMESTAMP, TestConstants.EMPTY_DITTO_HEADERS, TestConstants.METADATA);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullFeature() {
        FeatureCreated.of(TestConstants.Thing.THING_ID, null, TestConstants.Thing.REVISION_NUMBER,
                TestConstants.TIMESTAMP, TestConstants.EMPTY_DITTO_HEADERS, TestConstants.METADATA);
    }

    @Test
    public void toJsonReturnsExpected() {
        final FeatureCreated underTest =
                FeatureCreated.of(TestConstants.Thing.THING_ID, TestConstants.Feature.FLUX_CAPACITOR,
                        TestConstants.Thing.REVISION_NUMBER,
                        TestConstants.TIMESTAMP, TestConstants.EMPTY_DITTO_HEADERS,
                        TestConstants.METADATA);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void createInstanceFromValidJson() {
        final FeatureCreated underTest =
                FeatureCreated.fromJson(KNOWN_JSON.toString(), TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getFeature()).isEqualTo(TestConstants.Feature.FLUX_CAPACITOR);
    }

    @Test
    public void createInstanceFromJsonWithNullFeature() {
        final JsonObject inputJson =
                KNOWN_JSON.setValue(FeatureCreated.JSON_FEATURE.getPointer(), JsonFactory.nullLiteral());
        final FeatureCreated parsedEvent = FeatureCreated.fromJson(inputJson, DittoHeaders.empty());

        assertThat(parsedEvent.getFeature()).isEqualTo(
                ThingsModelFactory.nullFeature(TestConstants.Feature.FLUX_CAPACITOR_ID));
    }

    @Test
    public void getResourcePathReturnsExpected() {
        final JsonPointer expectedResourcePath =
                JsonFactory.newPointer("/features/" + TestConstants.Feature.FLUX_CAPACITOR_ID);

        final FeatureCreated underTest =
                FeatureCreated.of(TestConstants.Thing.THING_ID, TestConstants.Feature.FLUX_CAPACITOR,
                        TestConstants.Thing.REVISION_NUMBER,
                        TestConstants.TIMESTAMP,
                        TestConstants.EMPTY_DITTO_HEADERS,
                        TestConstants.METADATA);

        assertThat(underTest.getResourcePath()).isEqualTo(expectedResourcePath);
    }

}
