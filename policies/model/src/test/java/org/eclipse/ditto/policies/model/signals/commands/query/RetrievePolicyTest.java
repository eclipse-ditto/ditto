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
package org.eclipse.ditto.policies.model.signals.commands.query;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.commands.GlobalCommandRegistry;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseOptions;
import org.eclipse.ditto.json.assertions.DittoJsonAssertions;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommand;
import org.eclipse.ditto.policies.model.signals.commands.TestConstants;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicy}.
 */
public final class RetrievePolicyTest {

    private static final String SELECTED_FIELDS = "field1,field2,field3";

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(PolicyCommand.JsonFields.TYPE, RetrievePolicy.TYPE)
            .set(PolicyCommand.JsonFields.JSON_POLICY_ID, TestConstants.Policy.POLICY_ID.toString())
            .build();
    private static final JsonObject KNOWN_JSON_WITH_FIELD_SELECTION = JsonFactory.newObjectBuilder()
            .set(PolicyCommand.JsonFields.TYPE, RetrievePolicy.TYPE)
            .set(PolicyCommand.JsonFields.JSON_POLICY_ID, TestConstants.Policy.POLICY_ID.toString())
            .set(RetrievePolicy.JSON_SELECTED_FIELDS, SELECTED_FIELDS)
            .build();

    private static final JsonParseOptions JSON_PARSE_OPTIONS = JsonFactory.newParseOptionsBuilder()
            .withoutUrlDecoding()
            .build();
    private static final JsonFieldSelector JSON_FIELD_SELECTOR = JsonFactory.newFieldSelector(SELECTED_FIELDS,
            JSON_PARSE_OPTIONS);

    private static final DittoHeaders EMPTY_DITTO_HEADERS = DittoHeaders.empty();


    @Test
    public void assertImmutability() {
        assertInstancesOf(RetrievePolicy.class, areImmutable(),
                provided(JsonFieldSelector.class, PolicyId.class).areAlsoImmutable());
    }


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(RetrievePolicy.class)
                .withRedefinedSuperclass()
                .verify();
    }


    @Test
    public void tryToCreateInstanceWithNullPolicyId() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> RetrievePolicy.of(null, EMPTY_DITTO_HEADERS))
                .withNoCause();
    }

    @Test
    public void getSelectedFieldsReturnsExpected() {
        final RetrievePolicy underTest =
                RetrievePolicy.of(TestConstants.Policy.POLICY_ID, DittoHeaders.empty(), JSON_FIELD_SELECTOR);

        Assertions.assertThat(underTest.getSelectedFields()).contains(JSON_FIELD_SELECTOR);
    }

    @Test
    public void toJsonReturnsExpected() {
        final RetrievePolicy underTest = RetrievePolicy.of(
                TestConstants.Policy.POLICY_ID, EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void jsonSerializationWorksAsExpectedWithSelectedFields() {
        final RetrievePolicy underTest =
                RetrievePolicy.of(TestConstants.Policy.POLICY_ID, DittoHeaders.empty(), JSON_FIELD_SELECTOR);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        DittoJsonAssertions.assertThat(actualJson).isEqualTo(KNOWN_JSON_WITH_FIELD_SELECTION);
    }


    @Test
    public void createInstanceFromValidJsonWithSelectedFields() {
        final RetrievePolicy underTest = RetrievePolicy.fromJson(KNOWN_JSON_WITH_FIELD_SELECTION.toString(),
                DittoHeaders.empty());

        assertThat(underTest).isNotNull();
        Assertions.assertThat((CharSequence) underTest.getEntityId()).isEqualTo(TestConstants.Policy.POLICY_ID);
        Assertions.assertThat(underTest.getSelectedFields()).contains(JSON_FIELD_SELECTOR);
    }


    @Test
    public void createInstanceFromValidJson() {
        final RetrievePolicy underTest = RetrievePolicy.fromJson(KNOWN_JSON.toString(), EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat((CharSequence) underTest.getEntityId()).isEqualTo(TestConstants.Policy.POLICY_ID);
    }

    @Test
    public void parseRetrievePolicyCommand() {
        final GlobalCommandRegistry commandRegistry = GlobalCommandRegistry.getInstance();

        final RetrievePolicy command = RetrievePolicy.of(
                TestConstants.Policy.POLICY_ID, TestConstants.DITTO_HEADERS);
        final JsonObject jsonObject = command.toJson(FieldType.regularOrSpecial());

        final Command parsedCommand = commandRegistry.parse(jsonObject, TestConstants.DITTO_HEADERS);

        assertThat(parsedCommand).isEqualTo(command);
    }

}
