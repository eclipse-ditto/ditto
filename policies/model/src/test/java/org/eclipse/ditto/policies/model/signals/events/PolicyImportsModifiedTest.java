/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.base.model.signals.events.EventsourcedEvent;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyImports;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link PolicyImportsModified}.
 */
public final class PolicyImportsModifiedTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(Event.JsonFields.TIMESTAMP, TestConstants.TIMESTAMP.toString())
            .set(Event.JsonFields.TYPE, PolicyImportsModified.TYPE)
            .set(EventsourcedEvent.JsonFields.REVISION, TestConstants.Policy.REVISION_NUMBER)
            .set(Event.JsonFields.METADATA, TestConstants.METADATA.toJson())
            .set(PolicyEvent.JsonFields.POLICY_ID, TestConstants.Policy.POLICY_ID.toString())
            .set(PolicyImportsModified.JSON_POLICY_IMPORTS, TestConstants.Policy.POLICY_IMPORTS.toJson())
            .build();


    @Test
    public void assertImmutability() {
        assertInstancesOf(PolicyImportsModified.class, areImmutable(),
                provided(PolicyImports.class).isAlsoImmutable());
    }


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(PolicyImportsModified.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullPolicyId() {
        PolicyImportsModified.of((PolicyId) null, TestConstants.Policy.POLICY_IMPORTS,
                TestConstants.Policy.REVISION_NUMBER,
                TestConstants.TIMESTAMP,
                TestConstants.EMPTY_DITTO_HEADERS,
                null);
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullPolicyImports() {
        PolicyImportsModified.of(TestConstants.Policy.POLICY_ID, null,
                TestConstants.Policy.REVISION_NUMBER,
                TestConstants.TIMESTAMP,
                TestConstants.EMPTY_DITTO_HEADERS,
                null);
    }


    @Test
    public void toJsonReturnsExpected() {
        final PolicyImportsModified underTest = PolicyImportsModified.of(TestConstants.Policy.POLICY_ID,
                TestConstants.Policy.POLICY_IMPORTS,
                TestConstants.Policy.REVISION_NUMBER,
                TestConstants.TIMESTAMP,
                TestConstants.EMPTY_DITTO_HEADERS,
                TestConstants.METADATA);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualToIgnoringFieldDefinitions(KNOWN_JSON);
    }


    @Test
    public void createInstanceFromValidJson() {
        final PolicyImportsModified underTest =
                PolicyImportsModified.fromJson(KNOWN_JSON.toString(), TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat((CharSequence) underTest.getPolicyEntityId()).isEqualTo(TestConstants.Policy.POLICY_ID);
        assertThat(underTest.getPolicyImports()).isEqualTo(TestConstants.Policy.POLICY_IMPORTS);
    }

}
