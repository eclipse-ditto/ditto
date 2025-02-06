/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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

import java.lang.ref.SoftReference;

import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.base.model.signals.events.EventsourcedEvent;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ThingMigrated}.
 */
public final class ThingMigratedTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(Event.JsonFields.TIMESTAMP, TestConstants.TIMESTAMP.toString())
            .set(Event.JsonFields.TYPE, ThingMigrated.TYPE)
            .set(Event.JsonFields.METADATA, TestConstants.METADATA.toJson())
            .set(EventsourcedEvent.JsonFields.REVISION, TestConstants.Thing.REVISION_NUMBER)
            .set(ThingEvent.JsonFields.THING_ID, TestConstants.Thing.THING_ID.toString())
            .set(ThingMigrated.JsonFields.JSON_PATH, TestConstants.Thing.ABSOLUTE_LOCATION_ATTRIBUTE_POINTER.toString())
            .set(ThingMigrated.JsonFields.JSON_VALUE, TestConstants.Thing.LOCATION_ATTRIBUTE_VALUE)
            .build();

    /**
     * Ensures that equals and hashCode behave correctly for different instances.
     */
    @Test
    public void testHashCodeAndEquals() {
        final SoftReference<JsonObject> red = new SoftReference<>(JsonFactory.newObject("{\"foo\": 1}"));
        final SoftReference<JsonObject> black = new SoftReference<>(JsonFactory.newObject("{\"foo\": 2}"));

        EqualsVerifier.forClass(ThingMigrated.class)
                .withRedefinedSuperclass()
                .withPrefabValues(SoftReference.class, red, black)
                .verify();
    }

    /**
     * Tests if toJson correctly serializes a {@code ThingMigrated} event.
     */
    @Test
    public void toJsonReturnsExpected() {
        final ThingMigrated underTest =
                ThingMigrated.of(TestConstants.Thing.THING_ID,
                        TestConstants.Thing.ABSOLUTE_LOCATION_ATTRIBUTE_POINTER,
                        TestConstants.Thing.LOCATION_ATTRIBUTE_VALUE,
                        TestConstants.Thing.REVISION_NUMBER,
                        TestConstants.TIMESTAMP,
                        TestConstants.EMPTY_DITTO_HEADERS,
                        TestConstants.METADATA);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    /**
     * Verifies that a {@code ThingMigrated} instance can be created from valid JSON.
     */
    @Test
    public void createInstanceFromValidJson() {
        final ThingMigrated underTest =
                ThingMigrated.fromJson(KNOWN_JSON, TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat((Object) underTest.getEntityId()).isEqualTo(TestConstants.Thing.THING_ID);
        assertThat(underTest.getResourcePath()).isEqualTo(TestConstants.Thing.ABSOLUTE_LOCATION_ATTRIBUTE_POINTER);
        assertThat(underTest.getValue()).isEqualTo(TestConstants.Thing.LOCATION_ATTRIBUTE_VALUE);
        assertThat(underTest.getMetadata()).contains(TestConstants.METADATA);
        assertThat(underTest.getRevision()).isEqualTo(TestConstants.Thing.REVISION_NUMBER);
        assertThat(underTest.getTimestamp()).contains(TestConstants.TIMESTAMP);
    }
}
