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
    public void validationOfInvalidPolicyIdThrowsException() {
        final String policyId = "myPolicy";

        assertThatExceptionOfType(PolicyIdInvalidException.class)
                .isThrownBy(() -> PolicyIdValidator.getInstance().accept(policyId, DittoHeaders.empty()))
                .withNoCause();
    }

}
