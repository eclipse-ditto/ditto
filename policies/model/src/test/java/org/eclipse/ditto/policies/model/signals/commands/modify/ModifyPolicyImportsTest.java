/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.model.signals.commands.modify;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyIdInvalidException;
import org.eclipse.ditto.policies.model.PolicyImportInvalidException;
import org.eclipse.ditto.policies.model.PolicyImports;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommand;
import org.eclipse.ditto.policies.model.signals.commands.TestConstants;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ModifyPolicyImports}.
 */
public final class ModifyPolicyImportsTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(PolicyCommand.JsonFields.TYPE, ModifyPolicyImports.TYPE)
            .set(PolicyCommand.JsonFields.JSON_POLICY_ID, TestConstants.Policy.POLICY_ID.toString())
            .set(ModifyPolicyImports.JSON_POLICY_IMPORTS, TestConstants.Policy.POLICY_IMPORTS.toJson())
            .build();


    @Test
    public void assertImmutability() {
        assertInstancesOf(ModifyPolicyImports.class,
                areImmutable(),
                provided(Iterable.class, PolicyEntry.class, PolicyId.class, PolicyImports.class).isAlsoImmutable());
    }


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ModifyPolicyImports.class)
                .withRedefinedSuperclass()
                .verify();
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullPolicyId() {
        ModifyPolicyImports.of((PolicyId) null, TestConstants.Policy.POLICY_IMPORTS,
                TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test
    public void tryToCreateInstanceWithInvalidPolicyId() {
        assertThatExceptionOfType(PolicyIdInvalidException.class)
                .isThrownBy(() -> ModifyPolicyImports.of(PolicyId.of("undefined"), TestConstants.Policy.POLICY_IMPORTS,
                        TestConstants.EMPTY_DITTO_HEADERS));
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullPolicyImports() {
        ModifyPolicyImports.of(TestConstants.Policy.POLICY_ID, null,
                TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test(expected = PolicyImportInvalidException.class)
    public void tryToCreateInstanceWithSelfReference() {
        ModifyPolicyImports.of(TestConstants.Policy.POLICY_ID, TestConstants.Policy.getPolicyImports(TestConstants.Policy.POLICY_ID),
                TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test
    public void toJsonReturnsExpected() {
        final ModifyPolicyImports underTest = ModifyPolicyImports.of(
                TestConstants.Policy.POLICY_ID, TestConstants.Policy.POLICY_IMPORTS,
                TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson.toString()).isEqualTo(KNOWN_JSON.toString());
    }


    @Test
    public void createInstanceFromValidJson() {
        final ModifyPolicyImports underTest =
                ModifyPolicyImports.fromJson(KNOWN_JSON.toString(), TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getPolicyImports()).isEqualTo(TestConstants.Policy.POLICY_IMPORTS);
    }

}
