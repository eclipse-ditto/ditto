/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.model.signals.commands.query;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommand;
import org.eclipse.ditto.policies.model.signals.commands.TestConstants;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link RetrievePolicyEntryAllowedImportAdditions}.
 */
public final class RetrievePolicyEntryAllowedImportAdditionsTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(PolicyCommand.JsonFields.TYPE, RetrievePolicyEntryAllowedImportAdditions.TYPE)
            .set(PolicyCommand.JsonFields.JSON_POLICY_ID, TestConstants.Policy.POLICY_ID.toString())
            .set(RetrievePolicyEntryAllowedImportAdditions.JSON_LABEL, TestConstants.Policy.LABEL.toString())
            .build();

    private static final DittoHeaders EMPTY_DITTO_HEADERS = DittoHeaders.empty();

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(RetrievePolicyEntryAllowedImportAdditions.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test
    public void tryToCreateInstanceWithNullPolicyId() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> RetrievePolicyEntryAllowedImportAdditions.of(null,
                        TestConstants.Policy.LABEL, EMPTY_DITTO_HEADERS));
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullLabel() {
        RetrievePolicyEntryAllowedImportAdditions.of(TestConstants.Policy.POLICY_ID, null,
                EMPTY_DITTO_HEADERS);
    }

    @Test
    public void toJsonReturnsExpected() {
        final RetrievePolicyEntryAllowedImportAdditions underTest =
                RetrievePolicyEntryAllowedImportAdditions.of(TestConstants.Policy.POLICY_ID,
                        TestConstants.Policy.LABEL, EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void createInstanceFromValidJson() {
        final RetrievePolicyEntryAllowedImportAdditions underTest =
                RetrievePolicyEntryAllowedImportAdditions.fromJson(KNOWN_JSON.toString(), EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat((CharSequence) underTest.getEntityId()).isEqualTo(TestConstants.Policy.POLICY_ID);
        assertThat(underTest.getLabel()).isEqualTo(TestConstants.Policy.LABEL);
    }

}
