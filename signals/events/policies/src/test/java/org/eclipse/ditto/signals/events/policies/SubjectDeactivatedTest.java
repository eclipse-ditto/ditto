/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.signals.events.base.Event;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link SubjectDeactivated}.
 */
public final class SubjectDeactivatedTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(Event.JsonFields.TIMESTAMP, TestConstants.TIMESTAMP.toString())
            .set(Event.JsonFields.TYPE, SubjectDeactivated.TYPE)
            .set(Event.JsonFields.REVISION, TestConstants.Policy.REVISION_NUMBER)
            .set(PolicyEvent.JsonFields.POLICY_ID, TestConstants.Policy.POLICY_ID.toString())
            .set(SubjectDeactivated.JsonFields.LABEL, TestConstants.Policy.LABEL.toString())
            .set(SubjectDeactivated.JsonFields.SUBJECT_ID, TestConstants.Policy.SUBJECT.getId().toString())
            .build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(SubjectDeactivated.class, areImmutable(),
                provided(SubjectId.class, Label.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(SubjectDeactivated.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullPolicyId() {
        SubjectDeactivated.of(null, TestConstants.Policy.LABEL, TestConstants.Policy.SUBJECT_ID,
                TestConstants.Policy.REVISION_NUMBER, TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullLabel() {
        SubjectDeactivated.of(TestConstants.Policy.POLICY_ID, null, TestConstants.Policy.SUBJECT_ID,
                TestConstants.Policy.REVISION_NUMBER, TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullSubject() {
        SubjectDeactivated.of(TestConstants.Policy.POLICY_ID, TestConstants.Policy.LABEL, null,
                TestConstants.Policy.REVISION_NUMBER, TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test
    public void toJsonReturnsExpected() {
        final SubjectDeactivated underTest =
                SubjectDeactivated.of(TestConstants.Policy.POLICY_ID, TestConstants.Policy.LABEL,
                        TestConstants.Policy.SUBJECT_ID, TestConstants.Policy.REVISION_NUMBER, TestConstants.TIMESTAMP,
                        TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void createInstanceFromValidJson() {
        final SubjectDeactivated underTest =
                SubjectDeactivated.fromJson(KNOWN_JSON, TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat((CharSequence) underTest.getPolicyEntityId()).isEqualTo(TestConstants.Policy.POLICY_ID);
        assertThat(underTest.getLabel()).isEqualTo(TestConstants.Policy.LABEL);
    }

}
