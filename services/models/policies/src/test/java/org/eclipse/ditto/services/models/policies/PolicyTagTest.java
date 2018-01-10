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
 * Unit test for {@link PolicyTag}.
 */
public final class PolicyTagTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(PolicyTag.JsonFields.ID, TestConstants.Policy.POLICY_ID)
            .set(PolicyTag.JsonFields.REVISION, TestConstants.Policy.REVISION_NUMBER)
            .build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(PolicyTag.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(PolicyTag.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test
    public void toJsonReturnsExpected() {
        final PolicyTag underTest = PolicyTag.of(TestConstants.Policy.POLICY_ID, TestConstants.Policy.REVISION_NUMBER);
        final JsonValue jsonValue = underTest.toJson();

        assertThat(jsonValue).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void createInstanceFromValidJson() {

        final PolicyTag underTest = PolicyTag.fromJson(KNOWN_JSON);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getId()).isEqualTo(TestConstants.Policy.POLICY_ID);
        assertThat(underTest.getRevision()).isEqualTo(TestConstants.Policy.REVISION_NUMBER);
    }

}
