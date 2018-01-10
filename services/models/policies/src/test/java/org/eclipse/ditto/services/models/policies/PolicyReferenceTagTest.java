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
package org.eclipse.ditto.services.models.policies;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link PolicyReferenceTag}.
 */
public final class PolicyReferenceTagTest {

    private static final String ENTITY_ID = "ns:entityId";
    private static final PolicyTag POLICY_TAG =
            PolicyTag.of(TestConstants.Policy.POLICY_ID, TestConstants.Policy.REVISION_NUMBER);
    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(PolicyReferenceTag.JsonFields.ENTITY_ID, ENTITY_ID)
            .set(PolicyReferenceTag.JsonFields.POLICY_ID, POLICY_TAG.getId())
            .set(PolicyReferenceTag.JsonFields.POLICY_REV, POLICY_TAG.getRevision())
            .build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(PolicyReferenceTag.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(PolicyReferenceTag.class).verify();
    }

    @Test
    public void toJsonReturnsExpected() {
        final PolicyReferenceTag underTest = PolicyReferenceTag.of(ENTITY_ID, POLICY_TAG);
        final JsonValue jsonValue = underTest.toJson();

        assertThat(jsonValue).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void createInstanceFromValidJson() {
        final PolicyReferenceTag underTest = PolicyReferenceTag.fromJson(KNOWN_JSON);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getEntityId()).isEqualTo(ENTITY_ID);
        assertThat(underTest.getPolicyTag()).isEqualTo(POLICY_TAG);
    }

}
