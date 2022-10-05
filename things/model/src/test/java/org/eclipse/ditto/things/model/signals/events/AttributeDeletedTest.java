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

import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.base.model.signals.events.EventsourcedEvent;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link AttributeDeleted}.
 */
public final class AttributeDeletedTest {

    private static final JsonPointer KNOWN_ATTRIBUTE_POINTER = JsonFactory.newPointer("properties/target_year_3");

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(Event.JsonFields.TIMESTAMP, TestConstants.TIMESTAMP.toString())
            .set(Event.JsonFields.TYPE, AttributeDeleted.TYPE)
            .set(Event.JsonFields.METADATA, TestConstants.METADATA.toJson())
            .set(EventsourcedEvent.JsonFields.REVISION, TestConstants.Thing.REVISION_NUMBER)
            .set(ThingEvent.JsonFields.THING_ID, TestConstants.Thing.THING_ID.toString())
            .set(AttributeDeleted.JSON_ATTRIBUTE, KNOWN_ATTRIBUTE_POINTER.toString())
            .build();


    @Test
    public void assertImmutability() {
        assertInstancesOf(AttributeDeleted.class, areImmutable(), provided(JsonPointer.class).isAlsoImmutable());
    }


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(AttributeDeleted.class)
                .withRedefinedSuperclass().verify();
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullThingId() {
        AttributeDeleted.of(null, KNOWN_ATTRIBUTE_POINTER, TestConstants.Thing.REVISION_NUMBER,
                TestConstants.TIMESTAMP, TestConstants.EMPTY_DITTO_HEADERS, TestConstants.METADATA);
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullAttributeKey() {
        AttributeDeleted.of(TestConstants.Thing.THING_ID, null, TestConstants.Thing.REVISION_NUMBER,
                TestConstants.TIMESTAMP, TestConstants.EMPTY_DITTO_HEADERS, TestConstants.METADATA);
    }


    @Test
    public void toJsonReturnsExpected() {
        final AttributeDeleted underTest = AttributeDeleted.of(TestConstants.Thing.THING_ID, KNOWN_ATTRIBUTE_POINTER,
                TestConstants.Thing.REVISION_NUMBER, TestConstants.TIMESTAMP, TestConstants.EMPTY_DITTO_HEADERS,
                TestConstants.METADATA);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }


    @Test
    public void createInstanceFromValidJson() {
        final AttributeDeleted underTest =
                AttributeDeleted.fromJson(KNOWN_JSON.toString(), TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest.getAttributePointer()).isEqualTo(KNOWN_ATTRIBUTE_POINTER);
    }

}
