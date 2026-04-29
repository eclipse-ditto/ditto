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

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommandResponse;
import org.eclipse.ditto.policies.model.signals.commands.TestConstants;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link RetrievePolicyEntryAllowedAdditionsResponse}.
 */
public final class RetrievePolicyEntryAllowedAdditionsResponseTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(PolicyCommandResponse.JsonFields.TYPE, RetrievePolicyEntryAllowedAdditionsResponse.TYPE)
            .set(PolicyCommandResponse.JsonFields.STATUS, HttpStatus.OK.getCode())
            .set(PolicyCommandResponse.JsonFields.JSON_POLICY_ID, TestConstants.Policy.POLICY_ID.toString())
            .set(RetrievePolicyEntryAllowedAdditionsResponse.JSON_LABEL,
                    TestConstants.Policy.LABEL.toString())
            .set(RetrievePolicyEntryAllowedAdditionsResponse.JSON_ALLOWED_ADDITIONS,
                    TestConstants.Policy.ALLOWED_ADDITIONS.stream()
                            .map(a -> JsonValue.of(a.getName()))
                            .collect(JsonCollectors.valuesToArray()))
            .build();

    private static final DittoHeaders EMPTY_DITTO_HEADERS = DittoHeaders.empty();

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(RetrievePolicyEntryAllowedAdditionsResponse.class)
                .withRedefinedSuperclass()
                .usingGetClass()
                .verify();
    }

    @Test
    public void toJsonReturnsExpected() {
        final RetrievePolicyEntryAllowedAdditionsResponse underTest =
                RetrievePolicyEntryAllowedAdditionsResponse.of(TestConstants.Policy.POLICY_ID,
                        TestConstants.Policy.LABEL, TestConstants.Policy.ALLOWED_ADDITIONS,
                        EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void createInstanceFromValidJson() {
        final RetrievePolicyEntryAllowedAdditionsResponse underTest =
                RetrievePolicyEntryAllowedAdditionsResponse.fromJson(KNOWN_JSON, EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getAllowedAdditions()).isEqualTo(TestConstants.Policy.ALLOWED_ADDITIONS);
    }

}
