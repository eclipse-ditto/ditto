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

import java.lang.ref.SoftReference;

import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.base.model.signals.events.EventsourcedEvent;
import org.eclipse.ditto.base.model.signals.events.GlobalEventRegistry;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.things.model.Thing;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ThingCreated}.
 */
public final class ThingCreatedTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(Event.JsonFields.TIMESTAMP, TestConstants.TIMESTAMP.toString())
            .set(Event.JsonFields.TYPE, ThingCreated.TYPE)
            .set(Event.JsonFields.METADATA, TestConstants.METADATA.toJson())
            .set(EventsourcedEvent.JsonFields.REVISION, TestConstants.Thing.REVISION_NUMBER)
            .set(ThingEvent.JsonFields.THING_ID, TestConstants.Thing.THING_ID.toString())
            .set(ThingEvent.JsonFields.THING, TestConstants.Thing.THING.toJson(FieldType.regularOrSpecial()))
            .build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(ThingCreated.class, areImmutable(),
                provided(Thing.class).isAlsoImmutable());
    }


    @Test
    public void testHashCodeAndEquals() {
        final SoftReference<JsonObject> red = new SoftReference<>(JsonFactory.newObject("{\"foo\": 1}"));
        final SoftReference<JsonObject> black = new SoftReference<>(JsonFactory.newObject("{\"foo\": 2}"));

        EqualsVerifier.forClass(ThingCreated.class)
                .withRedefinedSuperclass()
                .withPrefabValues(SoftReference.class, red, black)
                .verify();
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullThing() {
        ThingCreated.of(null, TestConstants.Thing.REVISION_NUMBER,
                TestConstants.TIMESTAMP, TestConstants.EMPTY_DITTO_HEADERS, TestConstants.METADATA);
    }


    @Test
    public void toJsonReturnsExpected() {
        final ThingCreated underTest =
                ThingCreated.of(TestConstants.Thing.THING, TestConstants.Thing.REVISION_NUMBER, TestConstants.TIMESTAMP,
                        TestConstants.EMPTY_DITTO_HEADERS, TestConstants.METADATA);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }


    @Test
    public void createInstanceFromValidJson() {
        final ThingCreated underTest =
                ThingCreated.fromJson(KNOWN_JSON.toString(), TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getThing()).isEqualTo(TestConstants.Thing.THING);
    }

    @Test
    public void retrieveEventName() {
        final String name = ThingCreated.fromJson(KNOWN_JSON.toString(), TestConstants.EMPTY_DITTO_HEADERS).getName();
        assertThat(name).isEqualTo(ThingCreated.NAME);
    }

    @Test
    public void parseThingCreatedEvent() {
        final GlobalEventRegistry<ThingEvent<?>> eventRegistry = GlobalEventRegistry.getInstance();

        final ThingCreated event = ThingCreated.of(TestConstants.Thing.THING, TestConstants.Thing.REVISION_NUMBER,
                TestConstants.TIMESTAMP, TestConstants.DITTO_HEADERS, TestConstants.METADATA);
        final JsonObject jsonObject = event.toJson(FieldType.regularOrSpecial());

        final ThingEvent<?> parsedEvent = eventRegistry.parse(jsonObject, TestConstants.DITTO_HEADERS);

        assertThat(parsedEvent).isEqualTo(event);
    }

}
