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
package org.eclipse.ditto.signals.commands.things.modify;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.eclipse.ditto.signals.commands.things.assertions.ThingCommandAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.lang.ref.SoftReference;
import java.text.MessageFormat;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingIdInvalidException;
import org.eclipse.ditto.model.things.ThingTooLargeException;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.signals.commands.things.TestConstants;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
import org.eclipse.ditto.signals.commands.things.exceptions.PoliciesConflictingException;
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

    @Test
    public void createTooLargeThing() {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < TestConstants.THING_SIZE_LIMIT_BYTES; i++) {
            sb.append('a');
        }
        final JsonObject largeAttributes = JsonObject.newBuilder()
                .set("a", sb.toString())
                .build();
        final Thing thing = Thing.newBuilder()
                .setId("foo:bar")
                .setAttributes(largeAttributes)
                .build();

        assertThatThrownBy(() -> CreateThing.of(thing, null, DittoHeaders.empty()))
                .isInstanceOf(ThingTooLargeException.class);
    }

    @Test
    public void initializeWithInitialPolicyNullAndWithCopiedPolicyNull() {
        final CreateThing createThing =
                CreateThing.of(TestConstants.Thing.THING, null, null, TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(createThing.getInitialPolicy()).isNotPresent();
        assertThat(createThing.getPolicyIdOrPlaceholder()).isNotPresent();
    }

    @Test
    public void initializeWithCopiedPolicy() {
        final String thingReference = "{{ ref:things/my_namespace:my_thing/policyId }}";
        final CreateThing createThing =
                CreateThing.withCopiedPolicy(TestConstants.Thing.THING, thingReference,
                        TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(createThing.getInitialPolicy()).isNotPresent();
        assertThat(createThing.getPolicyIdOrPlaceholder()).isPresent();
        assertThat(createThing.getPolicyIdOrPlaceholder()).contains(thingReference);
    }

    @Test
    public void initializeWithCopiedPolicyAndWithInitialPolicyNullAndPolicyIdNull() {
        final Thing thing = TestConstants.Thing.THING.setPolicyId(null);
        final String thingReference = "{{ ref:things/my_namespace:my_thing/policyId }}";
        final CreateThing createThing =
                CreateThing.of(thing, null, thingReference, TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(createThing.getInitialPolicy()).isNotPresent();
        assertThat(createThing.getPolicyIdOrPlaceholder()).isPresent();
        assertThat(createThing.getPolicyIdOrPlaceholder()).contains(thingReference);
    }

    @Test
    public void initializeWithCopiedPolicyAndWithInitialPolicy() {
        final String thingReference = "{{ ref:things/my_namespace:my_thing/policyId }}";
        assertThatThrownBy(() ->
                CreateThing.of(TestConstants.Thing.THING, JsonObject.newBuilder().build(), thingReference,
                        TestConstants.EMPTY_DITTO_HEADERS))
                .isInstanceOf(PoliciesConflictingException.class)
                .hasMessage(MessageFormat.format(
                        "The Thing with ID ''{0}'' could not be created as it contained an inline Policy as" +
                                " well as a policyID to copy.", TestConstants.Thing.THING_ID));
    }
}
