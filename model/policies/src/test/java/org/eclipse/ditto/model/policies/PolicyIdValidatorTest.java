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
package org.eclipse.ditto.model.policies;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.junit.Test;

/**
 * Unit test for {@link PolicyIdValidator}.
 */
public final class PolicyIdValidatorTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(PolicyIdValidator.class, areImmutable());
    }

    @Test
    public void validationOfValidPolicyIdSucceeds() {
        final String policyId = "org.eclipse.ditto.test:myPolicy";

        PolicyIdValidator.getInstance().accept(policyId, DittoHeaders.empty());
    }

    @Test
    public void validationOfValidPolicyIdFailsWithEmptyName() {
        final String policyIdWithoutPolicyName = "org.eclipse.ditto.test:";

        assertThatExceptionOfType(PolicyIdInvalidException.class)
                .isThrownBy(() -> PolicyIdValidator.getInstance().accept(policyIdWithoutPolicyName, DittoHeaders.empty()))
                .withNoCause();
    }

    @Test
    public void validationOfValidPolicyIdFailsWithEmptyNamespace() {
        final String policyIdWithoutPolicyName = ":test";

        assertThatExceptionOfType(PolicyIdInvalidException.class)
                .isThrownBy(() -> PolicyIdValidator.getInstance().accept(policyIdWithoutPolicyName, DittoHeaders.empty()))
                .withNoCause();
    }

    @Test
    public void validationOfValidPolicyIdFailsWithEmptyNamespaceAndName() {
        final String policyIdWithoutPolicyName = ":";

        assertThatExceptionOfType(PolicyIdInvalidException.class)
                .isThrownBy(() -> PolicyIdValidator.getInstance().accept(policyIdWithoutPolicyName, DittoHeaders.empty()))
                .withNoCause();
    }

    @Test
    public void validationOfValidPolicyIdFailsWithOnlyNamespace() {
        final String policyIdWithoutPolicyName = "org.eclipse.ditto.test";

        assertThatExceptionOfType(PolicyIdInvalidException.class)
                .isThrownBy(() -> PolicyIdValidator.getInstance().accept(policyIdWithoutPolicyName, DittoHeaders.empty()))
                .withNoCause();
    }
}
