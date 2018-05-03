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
package org.eclipse.ditto.signals.commands.policies.query;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.PolicyIdInvalidException;
import org.eclipse.ditto.signals.commands.policies.PolicyCommand;
import org.eclipse.ditto.signals.commands.policies.TestConstants;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link RetrievePolicyEntry}.
 */
public final class RetrievePolicyEntryTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(PolicyCommand.JsonFields.TYPE, RetrievePolicyEntry.TYPE)
            .set(PolicyCommand.JsonFields.JSON_POLICY_ID, TestConstants.Policy.POLICY_ID)
            .set(RetrievePolicyEntry.JSON_LABEL, TestConstants.Policy.LABEL.toString())
            .build();

    private static final DittoHeaders EMPTY_DITTO_HEADERS = DittoHeaders.empty();


    @Test
    public void assertImmutability() {
        assertInstancesOf(RetrievePolicyEntry.class, areImmutable(),
                provided(Label.class, JsonFieldSelector.class).isAlsoImmutable());
    }


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(RetrievePolicyEntry.class)
                .withRedefinedSuperclass()
                .verify();
    }


    @Test
    public void tryToCreateInstanceWithNullPolicyId() {
        assertThatExceptionOfType(PolicyIdInvalidException.class)
                .isThrownBy(() -> RetrievePolicyEntry.of(null, TestConstants.Policy.LABEL, EMPTY_DITTO_HEADERS))
                .withNoCause();
    }


    @Test
    public void tryToCreateInstanceWithInvalidPolicyId() {
        assertThatExceptionOfType(PolicyIdInvalidException.class)
                .isThrownBy(() -> RetrievePolicyEntry.of("undefined", TestConstants.Policy.LABEL, EMPTY_DITTO_HEADERS))
                .withNoCause();
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullLabel() {
        RetrievePolicyEntry.of(TestConstants.Policy.POLICY_ID, null,
                EMPTY_DITTO_HEADERS);
    }


    @Test
    public void toJsonReturnsExpected() {
        final RetrievePolicyEntry underTest = RetrievePolicyEntry.of(
                TestConstants.Policy.POLICY_ID, TestConstants.Policy.LABEL, EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }


    @Test
    public void createInstanceFromValidJson() {
        final RetrievePolicyEntry underTest =
                RetrievePolicyEntry.fromJson(KNOWN_JSON.toString(), EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getId()).isEqualTo(TestConstants.Policy.POLICY_ID);
        assertThat(underTest.getLabel()).isEqualTo(TestConstants.Policy.LABEL);
    }

}
