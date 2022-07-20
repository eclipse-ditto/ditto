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
package org.eclipse.ditto.things.model.signals.commands.modify;

import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.lang.ref.SoftReference;
import java.text.MessageFormat;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.commands.GlobalCommandRegistry;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingTooLargeException;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.TestConstants;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.commands.exceptions.PoliciesConflictingException;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.Rule;
import org.junit.Test;


/**
 * Unit test for {@link CreateThing}.
 */
public final class CreateThingTest {

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

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

    @Test
    public void createInstanceWithValidThingId() {
        final Thing thing = ThingsModelFactory.newThingBuilder()
                .setLifecycle(TestConstants.Thing.LIFECYCLE)
                .setPolicyId(TestConstants.Thing.POLICY_ID)
                .setId(ThingId.of("test.ns", "foo-bar"))
                .build();

        final CreateThing createThing =
                CreateThing.of(thing, null, TestConstants.EMPTY_DITTO_HEADERS);

        softly.assertThat(createThing).isNotNull();
    }

    @Test
    public void toJsonReturnsExpected() {
        final CreateThing underTest =
                CreateThing.of(TestConstants.Thing.THING, null, TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        softly.assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }


    @Test
    public void createInstanceFromValidJson() {
        final CreateThing underTest = CreateThing.fromJson(KNOWN_JSON.toString(), TestConstants.EMPTY_DITTO_HEADERS);

        softly.assertThat(underTest).isNotNull();
        softly.assertThat(underTest.getThing()).isEqualTo(TestConstants.Thing.THING);
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
                .setId(ThingId.of("foo", "bar"))
                .setAttributes(largeAttributes)
                .build();

        softly.assertThatThrownBy(() -> CreateThing.of(thing, null, DittoHeaders.empty()))
                .isInstanceOf(ThingTooLargeException.class);
    }

    @Test
    public void createTooLargeMetadata() {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < TestConstants.THING_SIZE_LIMIT_BYTES; i++) {
            sb.append('a');
        }
        final Thing thing = ThingsModelFactory.newThingBuilder()
                .setLifecycle(TestConstants.Thing.LIFECYCLE)
                .setPolicyId(TestConstants.Thing.POLICY_ID)
                .setId(ThingId.of("test.ns", "foo-bar"))
                .setAttribute(JsonPointer.of("foo"), JsonValue.of("bar"))
                .setMetadata(Metadata.newBuilder()
                        .set("attributes/foo/meta", JsonObject.newBuilder()
                                .set("description", sb.toString())
                                .build())
                        .build())
                .build();

        softly.assertThatThrownBy(() -> CreateThing.of(thing, null, DittoHeaders.empty()))
                .isInstanceOf(ThingTooLargeException.class);
    }

    @Test
    public void initializeWithInitialPolicyNullAndWithCopiedPolicyNull() {
        final CreateThing createThing =
                CreateThing.of(TestConstants.Thing.THING, null, null, TestConstants.EMPTY_DITTO_HEADERS);

        softly.assertThat(createThing.getInitialPolicy()).isNotPresent();
        softly.assertThat(createThing.getPolicyIdOrPlaceholder()).isNotPresent();
    }

    @Test
    public void initializeWithCopiedPolicy() {
        final String thingReference = "{{ ref:things/my_namespace:my_thing/policyId }}";
        final CreateThing createThing =
                CreateThing.withCopiedPolicy(TestConstants.Thing.THING, thingReference,
                        TestConstants.EMPTY_DITTO_HEADERS);

        softly.assertThat(createThing.getInitialPolicy()).isNotPresent();
        softly.assertThat(createThing.getPolicyIdOrPlaceholder()).isPresent();
        softly.assertThat(createThing.getPolicyIdOrPlaceholder()).contains(thingReference);
    }

    @Test
    public void initializeWithCopiedPolicyAndWithInitialPolicyNullAndPolicyIdNull() {
        final Thing thing = TestConstants.Thing.THING.setPolicyId(null);
        final String thingReference = "{{ ref:things/my_namespace:my_thing/policyId }}";
        final CreateThing createThing =
                CreateThing.of(thing, null, thingReference, TestConstants.EMPTY_DITTO_HEADERS);

        softly.assertThat(createThing.getInitialPolicy()).isNotPresent();
        softly.assertThat(createThing.getPolicyIdOrPlaceholder()).isPresent();
        softly.assertThat(createThing.getPolicyIdOrPlaceholder()).contains(thingReference);
    }

    @Test
    public void initializeWithCopiedPolicyAndWithInitialPolicy() {
        final String thingReference = "{{ ref:things/my_namespace:my_thing/policyId }}";
        softly.assertThatThrownBy(() ->
                CreateThing.of(TestConstants.Thing.THING, JsonObject.newBuilder().build(), thingReference,
                        TestConstants.EMPTY_DITTO_HEADERS))
                .isInstanceOf(PoliciesConflictingException.class)
                .hasMessage(MessageFormat.format(
                        "The Thing with ID ''{0}'' could not be created as it contained an inline Policy as" +
                                " well as a policyID to copy.", TestConstants.Thing.THING_ID));
    }

    @Test
    public void parseCreateThingCommand() {
        final GlobalCommandRegistry commandRegistry = GlobalCommandRegistry.getInstance();

        final CreateThing command = CreateThing.of(TestConstants.Thing.THING, null,
                TestConstants.DITTO_HEADERS);
        final JsonObject jsonObject = command.toJson(FieldType.regularOrSpecial());

        final Command parsedCommand = commandRegistry.parse(jsonObject, TestConstants.DITTO_HEADERS);

        softly.assertThat(parsedCommand).isEqualTo(command);
    }

}
