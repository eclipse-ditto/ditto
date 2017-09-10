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
package org.eclipse.ditto.signals.commands.things.modify;

import static org.eclipse.ditto.signals.commands.things.assertions.ThingCommandAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.lang.ref.SoftReference;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingIdInvalidException;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.signals.commands.things.TestConstants;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;


/**
 * Unit test for {@link CreateThing}.
 */
public final class CreateThingTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(ThingCommand.JsonFields.TYPE, CreateThing.TYPE)
            .set(CreateThing.JSON_THING, TestConstants.Thing.THING.toJson(FieldType.regularOrSpecial()))
            .build();


    @Test
    public void assertImmutability() {
        assertInstancesOf(CreateThing.class,
                areImmutable(),
                provided(Thing.class, JsonObject.class).isAlsoImmutable());
    }


    @Test
    public void testHashCodeAndEquals() {
        final SoftReference<JsonObject> red = new SoftReference<>(JsonFactory.newObject("{\"foo\": 1}"));
        final SoftReference<JsonObject> black = new SoftReference<>(JsonFactory.newObject("{\"foo\": 2}"));

        EqualsVerifier.forClass(CreateThing.class)
                .withPrefabValues(SoftReference.class, red, black)
                .withRedefinedSuperclass()
                .verify();
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullThing() {
        CreateThing.of(null, null, TestConstants.EMPTY_DITTO_HEADERS);
    }


    @Test(expected = ThingIdInvalidException.class)
    public void tryToCreateInstanceWithInvalidThingId() {
        final Thing thing = ThingsModelFactory.newThingBuilder()
                .setLifecycle(TestConstants.Thing.LIFECYCLE)
                .setPolicyId(TestConstants.Thing.POLICY_ID)
                .setId("test.ns:foo bar")
                .build();

        CreateThing.of(thing, null, TestConstants.EMPTY_DITTO_HEADERS);
    }


    @Test
    public void createInstanceWithValidThingId() {
        final Thing thing = ThingsModelFactory.newThingBuilder()
                .setLifecycle(TestConstants.Thing.LIFECYCLE)
                .setPolicyId(TestConstants.Thing.POLICY_ID)
                .setId("test.ns:foo-bar")
                .build();

        final CreateThing createThing =
                CreateThing.of(thing, null, TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(createThing).isNotNull();
    }


    @Test
    public void toJsonReturnsExpected() {
        final CreateThing underTest =
                CreateThing.of(TestConstants.Thing.THING, null, TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }


    @Test
    public void createInstanceFromValidJson() {
        final CreateThing underTest = CreateThing.fromJson(KNOWN_JSON.toString(), TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getThing()).isEqualTo(TestConstants.Thing.THING);
    }

}
