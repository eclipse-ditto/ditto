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
package org.eclipse.ditto.policies.model.signals.events;

import static org.eclipse.ditto.base.model.assertions.DittoBaseAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.function.Predicate;

import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.base.model.signals.events.EventsourcedEvent;
import org.eclipse.ditto.base.model.signals.events.GlobalEventRegistry;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.model.Policy;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link PolicyCreated}.
 */
public final class PolicyCreatedTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(Event.JsonFields.TIMESTAMP, TestConstants.TIMESTAMP.toString())
            .set(Event.JsonFields.TYPE, PolicyCreated.TYPE)
            .set(EventsourcedEvent.JsonFields.REVISION, TestConstants.Policy.REVISION_NUMBER)
            .set(Event.JsonFields.METADATA, TestConstants.METADATA.toJson())
            .set(PolicyEvent.JsonFields.POLICY_ID, TestConstants.Policy.POLICY_ID.toString())
            .set(PolicyCreated.JSON_POLICY, TestConstants.Policy.POLICY.toJson(FieldType.regularOrSpecial()))
            .build();

    private static final JsonObject KNOWN_JSON_WITHOUT_REVISION = JsonFactory.newObjectBuilder()
            .set(Event.JsonFields.TIMESTAMP, TestConstants.TIMESTAMP.toString())
            .set(Event.JsonFields.TYPE, PolicyCreated.TYPE)
            .set(Event.JsonFields.METADATA, TestConstants.METADATA.toJson())
            .set(PolicyEvent.JsonFields.POLICY_ID, TestConstants.Policy.POLICY_ID.toString())
            .set(PolicyCreated.JSON_POLICY, TestConstants.Policy.POLICY.toJson(FieldType.regularOrSpecial()))
            .build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(PolicyCreated.class, areImmutable(), provided(Policy.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(PolicyCreated.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullPolicy() {
        PolicyCreated.of(null, TestConstants.Policy.REVISION_NUMBER,
                TestConstants.TIMESTAMP, TestConstants.EMPTY_DITTO_HEADERS, TestConstants.METADATA);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullDittoHeaders() {
        PolicyCreated.of(TestConstants.Policy.POLICY, TestConstants.Policy.REVISION_NUMBER,
                TestConstants.TIMESTAMP, null, TestConstants.METADATA);
    }

    @Test
    public void toJsonReturnsExpected() {
        final PolicyCreated underTest = PolicyCreated.of(TestConstants.Policy.POLICY,
                TestConstants.Policy.REVISION_NUMBER,
                TestConstants.TIMESTAMP, TestConstants.EMPTY_DITTO_HEADERS, TestConstants.METADATA);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void toJsonWithoutRevision() {
        final PolicyCreated underTest = PolicyCreated.of(TestConstants.Policy.POLICY,
                TestConstants.Policy.REVISION_NUMBER, TestConstants.TIMESTAMP, TestConstants.EMPTY_DITTO_HEADERS,
                TestConstants.METADATA);

        final Predicate<JsonField> isRevision =
                field -> field.getDefinition().map(definition -> definition == EventsourcedEvent.JsonFields.REVISION).orElse(false);
        final JsonObject actualJson = underTest.toJson(isRevision.negate().and(FieldType.regularOrSpecial()));

        assertThat(actualJson).isEqualTo(KNOWN_JSON_WITHOUT_REVISION);
    }

    @Test
    public void createInstanceFromValidJson() {
        final PolicyCreated underTest =
                PolicyCreated.fromJson(KNOWN_JSON.toString(), TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat((Jsonifiable<?>) underTest.getPolicy()).isEqualTo(TestConstants.Policy.POLICY);
    }

    @Test
    public void parsePolicyCreatedEvent() {
        final GlobalEventRegistry<PolicyEvent<?>> eventRegistry = GlobalEventRegistry.getInstance();

        final PolicyCreated event = PolicyCreated.of(TestConstants.Policy.POLICY, TestConstants.Policy.REVISION_NUMBER,
                TestConstants.TIMESTAMP, TestConstants.DITTO_HEADERS, TestConstants.METADATA);
        final JsonObject jsonObject = event.toJson(FieldType.regularOrSpecial());

        final PolicyEvent<?> parsedEvent = eventRegistry.parse(jsonObject, TestConstants.DITTO_HEADERS);

        assertThat(parsedEvent).isEqualTo(event);
    }

}
