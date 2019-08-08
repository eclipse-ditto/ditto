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
package org.eclipse.ditto.signals.commands.policies.modify;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.stream.StreamSupport;

import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.policies.PolicyEntry;
import org.eclipse.ditto.model.policies.id.PolicyId;
import org.eclipse.ditto.model.policies.id.PolicyIdInvalidException;
import org.eclipse.ditto.signals.commands.policies.PolicyCommand;
import org.eclipse.ditto.signals.commands.policies.TestConstants;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ModifyPolicyEntries}.
 */
public class ModifyPolicyEntriesTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(PolicyCommand.JsonFields.TYPE, ModifyPolicyEntries.TYPE)
            .set(PolicyCommand.JsonFields.JSON_POLICY_ID, TestConstants.Policy.POLICY_ID.toString())
            .set(ModifyPolicyEntries.JSON_POLICY_ENTRIES,
                    StreamSupport.stream(TestConstants.Policy.POLICY_ENTRIES.spliterator(), false)
                            .map(entry -> JsonFactory.newObjectBuilder()
                                    .set(entry.getLabel().toString(), entry.toJson(FieldType.regularOrSpecial()))
                                    .build())
                            .collect(JsonCollectors.objectsToObject()))
            .build();


    @Test
    public void assertImmutability() {
        assertInstancesOf(ModifyPolicyEntries.class,
                areImmutable(),
                provided(Iterable.class, PolicyEntry.class, PolicyId.class).isAlsoImmutable());
    }


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ModifyPolicyEntries.class)
                .withRedefinedSuperclass()
                .verify();
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullPolicyId() {
        ModifyPolicyEntries.of((PolicyId) null, TestConstants.Policy.POLICY_ENTRIES,
                TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test(expected = PolicyIdInvalidException.class)
    public void tryToCreateInstanceWithNullPolicyIdString() {
        ModifyPolicyEntries.of((String) null, TestConstants.Policy.POLICY_ENTRIES,
                TestConstants.EMPTY_DITTO_HEADERS);
    }


    @Test
    public void tryToCreateInstanceWithInvalidPolicyId() {
        assertThatExceptionOfType(PolicyIdInvalidException.class)
                .isThrownBy(() -> ModifyPolicyEntries.of("undefined", TestConstants.Policy.POLICY_ENTRIES,
                        TestConstants.EMPTY_DITTO_HEADERS));
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullPolicyEntries() {
        ModifyPolicyEntries.of(TestConstants.Policy.POLICY_ID, null,
                TestConstants.EMPTY_DITTO_HEADERS);
    }


    @Test
    public void toJsonReturnsExpected() {
        final ModifyPolicyEntries underTest = ModifyPolicyEntries.of(
                TestConstants.Policy.POLICY_ID, TestConstants.Policy.POLICY_ENTRIES,
                TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson.toString()).isEqualTo(KNOWN_JSON.toString());
    }


    @Test
    public void createInstanceFromValidJson() {
        final ModifyPolicyEntries underTest =
                ModifyPolicyEntries.fromJson(KNOWN_JSON.toString(), TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getPolicyEntries()).isEqualTo(TestConstants.Policy.POLICY_ENTRIES);
    }

}
