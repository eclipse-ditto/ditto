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
package org.eclipse.ditto.policies.model;

import static org.eclipse.ditto.policies.model.assertions.DittoPolicyAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Arrays;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutablePolicyImport}.
 */
public final class ImmutablePolicyImportTest {

    private static final PolicyId IMPORTED_POLICY_ID = PolicyId.of("com.example", "importablePolicy");

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutablePolicyImport.class,
                areImmutable(),
                provided(PolicyId.class, EffectedImports.class).areAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutablePolicyImport.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void testToAndFromJson() {
        final PolicyImport policyImport = ImmutablePolicyImport.of(IMPORTED_POLICY_ID,
                EffectedImports.newInstance(
                        Arrays.asList(Label.of("IncludedPolicyImport1"), Label.of("IncludedPolicyImport2"))
                )
        );

        final JsonObject policyImportJson = policyImport.toJson();
        final PolicyImport policyImport1 = ImmutablePolicyImport.fromJson(policyImport.getImportedPolicyId(), policyImportJson);

        assertThat(policyImport).isEqualTo(policyImport1);
    }

    @Test(expected = NullPointerException.class)
    public void testFromJsonWithNullLabel() {
        ImmutablePolicyImport.fromJson(null, JsonFactory.newObjectBuilder()
                .set("included", JsonFactory.newArray())
                .set("excluded", JsonFactory.newArray())
                .build());
    }

    @Test
    public void testFromJsonEmptyWithPolicyId() {
        final JsonObject jsonObject = JsonFactory.newObjectBuilder().build();

        final PolicyImport policyImport = ImmutablePolicyImport.fromJson(IMPORTED_POLICY_ID, jsonObject);
        assertThat(policyImport.getEffectedImports()).isEmpty();
    }

}
