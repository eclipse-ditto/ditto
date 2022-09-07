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
import org.eclipse.ditto.things.model.Features;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link FeaturesCreated}.
 */
public final class FeaturesCreatedTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(Event.JsonFields.TIMESTAMP, TestConstants.TIMESTAMP.toString())
            .set(Event.JsonFields.TYPE, FeaturesCreated.TYPE)
            .set(EventsourcedEvent.JsonFields.REVISION, TestConstants.Thing.REVISION_NUMBER)
            .set(Event.JsonFields.METADATA, TestConstants.METADATA.toJson())
            .set(ThingEvent.JsonFields.THING_ID, TestConstants.Thing.THING_ID.toString())
            .set(FeaturesCreated.JSON_FEATURES, TestConstants.Feature.FEATURES.toJson(FieldType.regularOrSpecial()))
            .build();


    @Test
    public void assertImmutability() {
        assertInstancesOf(FeaturesCreated.class, areImmutable(), provided(Features.class).isAlsoImmutable());
    }


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(FeaturesCreated.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullThingId() {
        FeaturesCreated.of(null, TestConstants.Feature.FEATURES, TestConstants.Thing.REVISION_NUMBER,
                TestConstants.TIMESTAMP, TestConstants.EMPTY_DITTO_HEADERS, TestConstants.METADATA);
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullFeatures() {
        FeaturesCreated.of(TestConstants.Thing.THING_ID, null, TestConstants.Thing.REVISION_NUMBER,
                TestConstants.TIMESTAMP, TestConstants.EMPTY_DITTO_HEADERS, TestConstants.METADATA);
    }

    @Test
    public void toJsonReturnsExpected() {
        final FeaturesCreated underTest =
                FeaturesCreated.of(TestConstants.Thing.THING_ID, TestConstants.Feature.FEATURES,
                        TestConstants.Thing.REVISION_NUMBER, TestConstants.TIMESTAMP,
                        TestConstants.EMPTY_DITTO_HEADERS, TestConstants.METADATA);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }


    @Test
    public void createInstanceFromValidJson() {
        final FeaturesCreated underTest =
                FeaturesCreated.fromJson(KNOWN_JSON.toString(), TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getFeatures()).isEqualTo(TestConstants.Feature.FEATURES);

    }


    @Test
    public void createInstanceFromJsonWithNullFeatures() {
        final JsonObject inputJson =
                KNOWN_JSON.setValue(FeaturesCreated.JSON_FEATURES.getPointer(), JsonFactory.nullLiteral());

        final FeaturesCreated parsedEvent = FeaturesCreated.fromJson(inputJson, DittoHeaders.empty());

        assertThat(parsedEvent.getFeatures()).isEqualTo(ThingsModelFactory.nullFeatures());
    }


    @Test
    public void getResourcePathReturnsExpected() {
        final JsonPointer expectedResourcePath = JsonFactory.newPointer("/features");

        final FeaturesCreated underTest =
                FeaturesCreated.of(TestConstants.Thing.THING_ID, TestConstants.Feature.FEATURES,
                        TestConstants.Thing.REVISION_NUMBER,
                        TestConstants.TIMESTAMP, TestConstants.EMPTY_DITTO_HEADERS, TestConstants.METADATA);

        assertThat(underTest.getResourcePath()).isEqualTo(expectedResourcePath);
    }

}
