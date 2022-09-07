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
package org.eclipse.ditto.policies.api;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.internal.models.streaming.EntityIdWithRevision;
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
            .set(EntityIdWithRevision.JsonFields.ENTITY_TYPE, TestConstants.Policy.POLICY_ID.getEntityType().toString())
            .set(PolicyTag.JsonFields.ENTITY_ID, TestConstants.Policy.POLICY_ID.toString())
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
        assertThat((CharSequence) underTest.getEntityId()).isEqualTo(TestConstants.Policy.POLICY_ID);
        assertThat(underTest.getRevision()).isEqualTo(TestConstants.Policy.REVISION_NUMBER);
    }

}
