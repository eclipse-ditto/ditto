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
package org.eclipse.ditto.signals.events.things;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.model.things.ThingIdInvalidException;
import org.eclipse.ditto.signals.events.base.Event;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link AttributesDeleted}.
 */
public final class AttributesDeletedTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(Event.JsonFields.TIMESTAMP, TestConstants.TIMESTAMP.toString())
            .set(Event.JsonFields.TYPE, AttributesDeleted.TYPE)
            .set(Event.JsonFields.REVISION, TestConstants.Thing.REVISION_NUMBER)
            .set(ThingEvent.JsonFields.THING_ID, TestConstants.Thing.THING_ID.toString())
            .build();


    @Test
    public void assertImmutability() {
        assertInstancesOf(AttributesDeleted.class, areImmutable(), provided(Attributes.class).isAlsoImmutable());
    }


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(AttributesDeleted.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test(expected = ThingIdInvalidException.class)
    public void tryToCreateInstanceWithNullThingIdString() {
        AttributesDeleted.of((String) null, TestConstants.Thing.REVISION_NUMBER, TestConstants.EMPTY_DITTO_HEADERS);
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullThingId() {
        AttributesDeleted.of((ThingId) null, TestConstants.Thing.REVISION_NUMBER, TestConstants.EMPTY_DITTO_HEADERS);
    }


    @Test
    public void toJsonReturnsExpected() {
        final AttributesDeleted underTest =
                AttributesDeleted.of(TestConstants.Thing.THING_ID, TestConstants.Thing.REVISION_NUMBER,
                        TestConstants.TIMESTAMP,
                        TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }


    @Test
    public void createInstanceFromValidJson() {
        final AttributesDeleted underTest =
                AttributesDeleted.fromJson(KNOWN_JSON.toString(), TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat((CharSequence) underTest.getThingEntityId()).isEqualTo(TestConstants.Thing.THING_ID);
    }

}
