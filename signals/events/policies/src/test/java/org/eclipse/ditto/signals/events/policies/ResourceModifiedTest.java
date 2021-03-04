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
package org.eclipse.ditto.signals.events.policies;

import static org.eclipse.ditto.model.base.assertions.DittoBaseAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.PolicyIdInvalidException;
import org.eclipse.ditto.model.policies.Resource;
import org.eclipse.ditto.signals.events.base.Event;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ResourceModified}.
 */
public final class ResourceModifiedTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(Event.JsonFields.TIMESTAMP, TestConstants.TIMESTAMP.toString())
            .set(Event.JsonFields.TYPE, ResourceModified.TYPE)
            .set(Event.JsonFields.REVISION, TestConstants.Policy.REVISION_NUMBER)
            .set(PolicyEvent.JsonFields.POLICY_ID, TestConstants.Policy.POLICY_ID.toString())
            .set(ResourceModified.JSON_LABEL, TestConstants.Policy.LABEL.toString())
            .set(ResourceModified.JSON_RESOURCE_KEY, TestConstants.Policy.RESOURCE.getFullQualifiedPath())
            .set(ResourceModified.JSON_RESOURCE,
                    TestConstants.Policy.RESOURCE.toJson(FieldType.regularOrSpecial()))
            .build();


    @Test
    public void assertImmutability() {
        assertInstancesOf(ResourceModified.class, areImmutable(),
                provided(Resource.class, Label.class).isAlsoImmutable());
    }


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ResourceModified.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test(expected = PolicyIdInvalidException.class)
    public void tryToCreateInstanceWithNullPolicyIdString() {
        ResourceModified.of((String) null, TestConstants.Policy.LABEL, TestConstants.Policy.RESOURCE,
                TestConstants.Policy.REVISION_NUMBER, TestConstants.EMPTY_DITTO_HEADERS);
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullPolicyId() {
        ResourceModified.of((PolicyId) null, TestConstants.Policy.LABEL, TestConstants.Policy.RESOURCE,
                TestConstants.Policy.REVISION_NUMBER, TestConstants.EMPTY_DITTO_HEADERS);
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullLabel() {
        ResourceModified.of(TestConstants.Policy.POLICY_ID, null, TestConstants.Policy.RESOURCE,
                TestConstants.Policy.REVISION_NUMBER, TestConstants.EMPTY_DITTO_HEADERS);
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullResource() {
        ResourceModified.of(TestConstants.Policy.POLICY_ID, TestConstants.Policy.LABEL, null,
                TestConstants.Policy.REVISION_NUMBER, TestConstants.EMPTY_DITTO_HEADERS);
    }


    @Test
    public void toJsonReturnsExpected() {
        final ResourceModified underTest =
                ResourceModified.of(TestConstants.Policy.POLICY_ID, TestConstants.Policy.LABEL,
                        TestConstants.Policy.RESOURCE, TestConstants.Policy.REVISION_NUMBER, TestConstants.TIMESTAMP,
                        TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }


    @Test
    public void createInstanceFromValidJson() {
        final ResourceModified underTest =
                ResourceModified.fromJson(KNOWN_JSON.toString(), TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat((CharSequence) underTest.getPolicyEntityId()).isEqualTo(TestConstants.Policy.POLICY_ID);
        assertThat(underTest.getLabel()).isEqualTo(TestConstants.Policy.LABEL);
        assertThat(underTest.getResource()).isEqualTo(TestConstants.Policy.RESOURCE);
    }

}
