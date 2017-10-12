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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.model.policies.TestConstants.Policy.RESOURCE_PATH;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.junit.Test;

/**
 * Unit test for {@link PoliciesResourceTypeTest}.
 */
public final class PoliciesResourceTypeTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(PoliciesResourceType.class, areImmutable());
    }

    @Test
    public void tryToGetPolicyResourceWithNullResourcePath() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> PoliciesResourceType.policyResource(null))
                .withMessage("The %s must not be null!", "resource path")
                .withNoCause();
    }

    @Test
    public void policyResourceReturnsExpected() {
        final ResourceKey expected = createExpectedResourceKey(PoliciesResourceType.POLICY);
        final ResourceKey actual = PoliciesResourceType.policyResource(RESOURCE_PATH);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void thingResourceReturnsExpected() {
        final ResourceKey expected = createExpectedResourceKey(PoliciesResourceType.THING);
        final ResourceKey actual = PoliciesResourceType.thingResource(RESOURCE_PATH);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void messageResourceReturnsExpected() {
        final ResourceKey expected = createExpectedResourceKey(PoliciesResourceType.MESSAGE);
        final ResourceKey actual = PoliciesResourceType.messageResource(RESOURCE_PATH);

        assertThat(actual).isEqualTo(expected);
    }

    private static ResourceKey createExpectedResourceKey(final CharSequence resourceType) {
        return PoliciesModelFactory.newResourceKey(resourceType, RESOURCE_PATH);
    }

}
