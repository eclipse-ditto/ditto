/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.signals.commands.policies.actions;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.signals.commands.policies.PolicyCommandResponse;
import org.eclipse.ditto.signals.commands.policies.TestConstants;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link TopLevelActionCommandResponse}.
 */
public final class TopLevelActionCommandResponseTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(PolicyCommandResponse.JsonFields.TYPE, TopLevelActionCommandResponse.TYPE)
            .set(PolicyCommandResponse.JsonFields.STATUS, TopLevelActionCommandResponse.STATUS.toInt())
            .set(PolicyCommandResponse.JsonFields.JSON_POLICY_ID, TestConstants.Policy.POLICY_ID.toString())
            .build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(TopLevelActionCommandResponse.class,
                areImmutable(),
                provided(PolicyId.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(TopLevelActionCommandResponse.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullPolicyId() {
        TopLevelActionCommandResponse.of(null, TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test
    public void toJsonReturnsExpected() {
        final TopLevelActionCommandResponse underTest =
                TopLevelActionCommandResponse.of(TestConstants.Policy.POLICY_ID,
                        TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void createInstanceFromValidJson() {
        final TopLevelActionCommandResponse underTest =
                TopLevelActionCommandResponse.fromJson(KNOWN_JSON, TestConstants.EMPTY_DITTO_HEADERS);

        final TopLevelActionCommandResponse expectedCommand =
                TopLevelActionCommandResponse.of(TestConstants.Policy.POLICY_ID,
                        TestConstants.EMPTY_DITTO_HEADERS);
        assertThat(underTest).isEqualTo(expectedCommand);
    }

}
