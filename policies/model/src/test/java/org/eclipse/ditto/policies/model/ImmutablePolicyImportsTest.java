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
import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutablePolicyImports}.
 */
public final class ImmutablePolicyImportsTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutablePolicyImports.class,
                areImmutable(),
                assumingFields("policyImports").areSafelyCopiedUnmodifiableCollectionsWithImmutableElements());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutablePolicyImports.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void testToAndFromJson() {
        final Collection<PolicyImport> policyImportList = new ArrayList<>();

        policyImportList.add(PolicyImport.newInstance(PolicyId.of("com.example", "firstPolicy"), EffectedImports.newInstance(null, null)));
        policyImportList.add(PolicyImport.newInstance(PolicyId.of("com.example", "secondPolicy"),
                        EffectedImports.newInstance(Arrays.asList(Label.of("includedLabel1")), null)));
        policyImportList.add(PolicyImport.newInstance(PolicyId.of("com.example", "thirdPolicy"),
                        EffectedImports.newInstance(null, Arrays.asList(Label.of("excludedLabel1")))));
        policyImportList.add(PolicyImport.newInstance(PolicyId.of("com.example", "fourthPolicy"),
                        EffectedImports.newInstance(Arrays.asList(Label.of("includedLabel1"), Label.of("includedLabel2")),
                                Arrays.asList(Label.of("excludedLabel2")))));

        final ImmutablePolicyImports policyImports = ImmutablePolicyImports.of(policyImportList);

        final JsonObject policyImportsJson = policyImports.toJson();
        final PolicyImports PolicyImportsFromJson = ImmutablePolicyImports.fromJson(policyImportsJson);

        assertThat(policyImports).isEqualTo(PolicyImportsFromJson);
    }

    @Test(expected = IllegalArgumentException.class)
    public void importPoliciesWithSameIdsShouldFail() {
        final Collection<PolicyImport> policyImportList = new ArrayList<>();
        policyImportList.add(PolicyImport.newInstance(PolicyId.of("com.example", "firstPolicy"),
                        EffectedImports.newInstance(null, null)));
        policyImportList.add(PolicyImport.newInstance(PolicyId.of("com.example", "firstPolicy"),
                        EffectedImports.newInstance(Arrays.asList(Label.of("includedLabel1")), null)));

        ImmutablePolicyImports.of(policyImportList);
    }

}