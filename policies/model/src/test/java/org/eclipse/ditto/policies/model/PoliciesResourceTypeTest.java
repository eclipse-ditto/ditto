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
package org.eclipse.ditto.policies.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.junit.Test;

/**
 * Unit test for {@link PoliciesResourceType}.
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
        final ResourceKey actual = PoliciesResourceType.policyResource(TestConstants.Policy.RESOURCE_PATH);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void thingResourceReturnsExpected() {
        final ResourceKey expected = createExpectedResourceKey(PoliciesResourceType.THING);
        final ResourceKey actual = PoliciesResourceType.thingResource(TestConstants.Policy.RESOURCE_PATH);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void messageResourceReturnsExpected() {
        final ResourceKey expected = createExpectedResourceKey(PoliciesResourceType.MESSAGE);
        final ResourceKey actual = PoliciesResourceType.messageResource(TestConstants.Policy.RESOURCE_PATH);

        assertThat(actual).isEqualTo(expected);
    }

    private static ResourceKey createExpectedResourceKey(final CharSequence resourceType) {
        return PoliciesModelFactory.newResourceKey(resourceType, TestConstants.Policy.RESOURCE_PATH);
    }

}
