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

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.base.model.signals.events.EventsourcedEvent;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ResourceDeleted}.
 */
public final class ResourceDeletedTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(Event.JsonFields.TIMESTAMP, TestConstants.TIMESTAMP.toString())
            .set(Event.JsonFields.TYPE, ResourceDeleted.TYPE)
            .set(EventsourcedEvent.JsonFields.REVISION, TestConstants.Policy.REVISION_NUMBER)
            .set(Event.JsonFields.METADATA, TestConstants.METADATA.toJson())
            .set(PolicyEvent.JsonFields.POLICY_ID, TestConstants.Policy.POLICY_ID.toString())
            .set(ResourceDeleted.JSON_LABEL, TestConstants.Policy.LABEL.toString())
            .set(ResourceDeleted.JSON_RESOURCE_KEY, TestConstants.Policy.RESOURCE_KEY.toString())
            .build();


    @Test
    public void assertImmutability() {
        assertInstancesOf(ResourceDeleted.class, areImmutable(),
                provided(JsonPointer.class, ResourceKey.class, Label.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ResourceDeleted.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullPolicyId() {
        ResourceDeleted.of(null, TestConstants.Policy.LABEL, TestConstants.Policy.RESOURCE_KEY,
                TestConstants.Policy.REVISION_NUMBER, TestConstants.TIMESTAMP, TestConstants.EMPTY_DITTO_HEADERS,
                TestConstants.METADATA);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullLabel() {
        ResourceDeleted.of(TestConstants.Policy.POLICY_ID, null, TestConstants.Policy.RESOURCE_KEY,
                TestConstants.Policy.REVISION_NUMBER, TestConstants.TIMESTAMP, TestConstants.EMPTY_DITTO_HEADERS,
                TestConstants.METADATA);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullResource() {
        ResourceDeleted.of(TestConstants.Policy.POLICY_ID, TestConstants.Policy.LABEL, null,
                TestConstants.Policy.REVISION_NUMBER, TestConstants.TIMESTAMP, TestConstants.EMPTY_DITTO_HEADERS,
                TestConstants.METADATA);
    }

    @Test
    public void toJsonReturnsExpected() {
        final ResourceDeleted underTest = ResourceDeleted.of(TestConstants.Policy.POLICY_ID, TestConstants.Policy.LABEL,
                TestConstants.Policy.RESOURCE_KEY, TestConstants.Policy.REVISION_NUMBER,
                TestConstants.TIMESTAMP, TestConstants.EMPTY_DITTO_HEADERS, TestConstants.METADATA);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }


    @Test
    public void createInstanceFromValidJson() {
        final ResourceDeleted underTest =
                ResourceDeleted.fromJson(KNOWN_JSON.toString(), TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat((CharSequence) underTest.getPolicyEntityId()).isEqualTo(TestConstants.Policy.POLICY_ID);
        assertThat(underTest.getLabel()).isEqualTo(TestConstants.Policy.LABEL);
        assertThat(underTest.getResourceKey()).isEqualTo(TestConstants.Policy.RESOURCE_KEY);
    }

}
