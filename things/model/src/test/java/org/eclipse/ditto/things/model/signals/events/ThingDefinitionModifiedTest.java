/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingDefinition;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ThingDefinitionModified}.
 */
public class ThingDefinitionModifiedTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(Event.JsonFields.TYPE, ThingDefinitionModified.TYPE)
            .set(Event.JsonFields.TIMESTAMP, TestConstants.TIMESTAMP.toString())
            .set(Event.JsonFields.METADATA, TestConstants.METADATA.toJson())
            .set(EventsourcedEvent.JsonFields.REVISION, TestConstants.Thing.REVISION_NUMBER)
            .set(ThingEvent.JsonFields.THING_ID, TestConstants.Thing.THING_ID.toString())
            .set(ThingDefinitionModified.JSON_DEFINITION, JsonValue.of(TestConstants.Thing.DEFINITION.toString()))
            .build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(ThingDefinitionModified.class,
                areImmutable(),
                provided(ThingDefinition.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ThingDefinitionModified.class)
                .withRedefinedSuperclass()
                .verify();
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullThingId() {
        ThingDefinitionModified.of(null, TestConstants.Thing.DEFINITION, TestConstants.Thing.REVISION_NUMBER,
                TestConstants.TIMESTAMP, TestConstants.EMPTY_DITTO_HEADERS, TestConstants.METADATA);
    }

    @Test
    public void toJsonReturnsExpected() {
        final ThingDefinitionModified underTest =
                ThingDefinitionModified.of(TestConstants.Thing.THING_ID, TestConstants.Thing.DEFINITION,
                        TestConstants.Thing.REVISION_NUMBER, TestConstants.TIMESTAMP,
                        TestConstants.EMPTY_DITTO_HEADERS, TestConstants.METADATA);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void createInstanceFromValidJson() {
        final ThingDefinitionModified underTest =
                ThingDefinitionModified.fromJson(KNOWN_JSON.toString(), TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getThingDefinition()).isEqualTo(TestConstants.Thing.DEFINITION);
    }

    @Test
    public void getResourcePathReturnsExpected() {
        final JsonPointer expectedResourcePath = Thing.JsonFields.DEFINITION.getPointer();
        final ThingDefinitionModified underTest =
                ThingDefinitionModified.of(TestConstants.Thing.THING_ID, TestConstants.Thing.DEFINITION,
                        TestConstants.Thing.REVISION_NUMBER, TestConstants.TIMESTAMP,
                        TestConstants.EMPTY_DITTO_HEADERS, TestConstants.METADATA);

        assertThat(underTest.getResourcePath()).isEqualTo(expectedResourcePath);
    }

}
