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

import static org.eclipse.ditto.base.model.assertions.DittoBaseAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.base.model.signals.events.EventsourcedEvent;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PolicyId;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link PolicyImportDeleted}.
 */
public final class PolicyImportDeletedTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(Event.JsonFields.TIMESTAMP, TestConstants.TIMESTAMP.toString())
            .set(Event.JsonFields.TYPE, PolicyImportDeleted.TYPE)
            .set(EventsourcedEvent.JsonFields.REVISION, TestConstants.Policy.REVISION_NUMBER)
            .set(Event.JsonFields.METADATA, TestConstants.METADATA.toJson())
            .set(PolicyEvent.JsonFields.POLICY_ID, TestConstants.Policy.POLICY_ID.toString())
            .set(PolicyImportDeleted.JSON_IMPORTED_POLICY_ID, TestConstants.Policy.IMPORTED_POLICY_ID.toString())
            .build();


    @Test
    public void assertImmutability() {
        assertInstancesOf(PolicyImportDeleted.class, areImmutable(), provided(Label.class).isAlsoImmutable());
    }


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(PolicyImportDeleted.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullPolicyId() {
        PolicyImportDeleted.of((PolicyId) null, TestConstants.Policy.IMPORTED_POLICY_ID,
                TestConstants.Policy.REVISION_NUMBER,
                TestConstants.TIMESTAMP,
                TestConstants.EMPTY_DITTO_HEADERS,
                null);
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullLabel() {
        PolicyImportDeleted.of(TestConstants.Policy.POLICY_ID, null, TestConstants.Policy.REVISION_NUMBER,
                TestConstants.TIMESTAMP,
                TestConstants.EMPTY_DITTO_HEADERS,
                null);
    }


    @Test
    public void toJsonReturnsExpected() {
        final PolicyImportDeleted underTest = PolicyImportDeleted.of(TestConstants.Policy.POLICY_ID,
                TestConstants.Policy.IMPORTED_POLICY_ID, TestConstants.Policy.REVISION_NUMBER,
                TestConstants.TIMESTAMP,
                TestConstants.EMPTY_DITTO_HEADERS,
                TestConstants.METADATA);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }


    @Test
    public void createInstanceFromValidJson() {
        final PolicyImportDeleted underTest =
                PolicyImportDeleted.fromJson(KNOWN_JSON.toString(), TestConstants.EMPTY_DITTO_HEADERS);

        Assertions.assertThat(underTest).isNotNull();
        assertThat((CharSequence) underTest.getPolicyEntityId()).isEqualTo(TestConstants.Policy.POLICY_ID);
        assertThat((CharSequence) underTest.getImportedPolicyId()).isEqualTo(TestConstants.Policy.IMPORTED_POLICY_ID);
    }

}
