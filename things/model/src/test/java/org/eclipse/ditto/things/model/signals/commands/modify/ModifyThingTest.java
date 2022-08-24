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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.eclipse.ditto.things.model.signals.commands.assertions.ThingCommandAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.text.MessageFormat;
import java.util.Optional;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.json.assertions.DittoJsonAssertions;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingTooLargeException;
import org.eclipse.ditto.things.model.signals.commands.TestConstants;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.commands.exceptions.PoliciesConflictingException;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ModifyThing}.
 */
public final class ModifyThingTest {

    private static final String POLICY_ID_TO_COPY = "somenamespace:somepolicyid";

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(ThingCommand.JsonFields.TYPE, ModifyThing.TYPE)
            .set(ThingCommand.JsonFields.JSON_THING_ID, TestConstants.Thing.THING_ID.toString())
            .set(ModifyThing.JSON_THING, TestConstants.Thing.THING.toJson(FieldType.regularOrSpecial()))
            .build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(ModifyThing.class,
                areImmutable(),
                provided(Thing.class, JsonObject.class, ThingId.class).isAlsoImmutable());
    }


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ModifyThing.class)
                .withRedefinedSuperclass()
                .verify();
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullThing() {
        ModifyThing.of(TestConstants.Thing.THING_ID, null, null, TestConstants.EMPTY_DITTO_HEADERS);
    }


    @Test
    public void toJsonReturnsExpected() {
        final ModifyThing underTest =
                ModifyThing.of(TestConstants.Thing.THING_ID, TestConstants.Thing.THING, null,
                        TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        DittoJsonAssertions.assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void toJsonWithPolicyIdToCopyReturnsExpected() {
        final JsonObject expected = KNOWN_JSON.set(ModifyThing.JSON_POLICY_ID_OR_PLACEHOLDER, POLICY_ID_TO_COPY);
        final ModifyThing underTest =
                ModifyThing.withCopiedPolicy(TestConstants.Thing.THING_ID, TestConstants.Thing.THING, POLICY_ID_TO_COPY,
                        TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        DittoJsonAssertions.assertThat(actualJson).isEqualTo(expected);
    }

    @Test
    public void toJsonWithInitialPolicyReturnsExpected() {

        final JsonObject expected = KNOWN_JSON.set(ModifyThing.JSON_INITIAL_POLICY, JsonObject.newBuilder().build());
        final ModifyThing underTest =
                ModifyThing.of(TestConstants.Thing.THING_ID, TestConstants.Thing.THING, JsonObject.newBuilder().build(),
                        TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        DittoJsonAssertions.assertThat(actualJson).isEqualTo(expected);
    }

    @Test
    public void getEntityReturnsExpected() {
        final JsonObject expected = TestConstants.Thing.THING.toJson(FieldType.regularOrSpecial());

        final ModifyThing underTest =
                ModifyThing.of(TestConstants.Thing.THING_ID, TestConstants.Thing.THING, null,
                        TestConstants.EMPTY_DITTO_HEADERS);

        final Optional<JsonValue> entity = underTest.getEntity();

        Assertions.assertThat(entity).contains(expected);
    }

    @Test
    public void getEntityWithInitialPolicyReturnsExpected() {
        final JsonObject expected = TestConstants.Thing.THING.toJson(FieldType.regularOrSpecial())
                .set(ModifyThing.JSON_INLINE_POLICY, JsonObject.newBuilder().build());

        final ModifyThing underTest =
                ModifyThing.of(TestConstants.Thing.THING_ID, TestConstants.Thing.THING, JsonObject.newBuilder().build(),
                        TestConstants.EMPTY_DITTO_HEADERS);

        final Optional<JsonValue> entity = underTest.getEntity();

        Assertions.assertThat(entity).contains(expected);
    }

    @Test
    public void getEntityWithPolicyIdToCopyReturnsExpected() {
        final JsonObject expected = TestConstants.Thing.THING.toJson(FieldType.regularOrSpecial())
                .set(ModifyThing.JSON_COPY_POLICY_FROM, POLICY_ID_TO_COPY);

        final ModifyThing underTest =
                ModifyThing.withCopiedPolicy(TestConstants.Thing.THING_ID, TestConstants.Thing.THING, POLICY_ID_TO_COPY,
                        TestConstants.EMPTY_DITTO_HEADERS);

        final Optional<JsonValue> entity = underTest.getEntity();

        Assertions.assertThat(entity).contains(expected);
    }

    @Test
    public void createInstanceFromValidJson() {
        final ModifyThing underTest = ModifyThing.fromJson(KNOWN_JSON.toString(), TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getThing()).isEqualTo(TestConstants.Thing.THING);
    }

    @Test
    public void modifyTooLargeThing() {
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

        assertThatThrownBy(() -> ModifyThing.of(thing.getEntityId().get(), thing, null, DittoHeaders.empty()))
                .isInstanceOf(ThingTooLargeException.class);
    }

    @Test
    public void initializeWithInitialPolicyNullAndWithCopiedPolicyNull() {
        final DittoHeaders v2Headers = DittoHeaders.newBuilder().schemaVersion(JsonSchemaVersion.LATEST).build();
        final Thing thingWithoutPolicy = TestConstants.Thing.THING.toBuilder()
                .removePolicyId()
                .build();

        final ModifyThing modifyThing =
                ModifyThing.of(TestConstants.Thing.THING_ID, thingWithoutPolicy, null, null, v2Headers);

        assertThat(modifyThing.getInitialPolicy()).isNotPresent();
        assertThat(modifyThing.getPolicyIdOrPlaceholder()).isNotPresent();
    }

    @Test
    public void initializeWithCopiedPolicy() {
        final DittoHeaders v2Headers = DittoHeaders.newBuilder().schemaVersion(JsonSchemaVersion.LATEST).build();
        final Thing thingWithoutPolicy = TestConstants.Thing.THING.toBuilder()
                .removePolicyId()
                .build();
        final String thingReference = "{{ ref:things/my_namespace:my_thing/policyId }}";

        final ModifyThing modifyThing =
                ModifyThing.withCopiedPolicy(TestConstants.Thing.THING_ID, thingWithoutPolicy, thingReference,
                        v2Headers);

        assertThat(modifyThing.getInitialPolicy()).isNotPresent();
        assertThat(modifyThing.getPolicyIdOrPlaceholder()).isPresent();
        assertThat(modifyThing.getPolicyIdOrPlaceholder()).contains(thingReference);
    }

    @Test
    public void initializeWithCopiedPolicyAndWithInitialPolicyNull() {
        final DittoHeaders v2Headers = DittoHeaders.newBuilder().schemaVersion(JsonSchemaVersion.LATEST).build();
        final Thing thingWithoutPolicy = TestConstants.Thing.THING.toBuilder()
                .removePolicyId()
                .build();
        final String thingReference = "{{ ref:things/my_namespace:my_thing/policyId }}";

        final ModifyThing modifyThing =
                ModifyThing.of(TestConstants.Thing.THING_ID, thingWithoutPolicy, null, thingReference,
                        v2Headers);

        assertThat(modifyThing.getInitialPolicy()).isNotPresent();
        assertThat(modifyThing.getPolicyIdOrPlaceholder()).isPresent();
        assertThat(modifyThing.getPolicyIdOrPlaceholder()).contains(thingReference);
    }

    @Test
    public void initializeWithCopiedPolicyAndWithInitialPolicy() {
        final DittoHeaders v2Headers = DittoHeaders.newBuilder().schemaVersion(JsonSchemaVersion.LATEST).build();
        final Thing thingWithoutPolicy = TestConstants.Thing.THING.toBuilder()
                .removePolicyId()
                .build();
        final String thingReference = "{{ ref:things/my_namespace:my_thing/policyId }}";

        assertThatThrownBy(() ->
                ModifyThing.of(TestConstants.Thing.THING_ID, thingWithoutPolicy, JsonObject.newBuilder().build(),
                        thingReference,
                        v2Headers))
                .isInstanceOf(PoliciesConflictingException.class)
                .hasMessage(MessageFormat.format(
                        "The Thing with ID ''{0}'' could not be created as it contained an inline " +
                                "Policy as well as a policyID to copy.", TestConstants.Thing.THING_ID));
    }
}
