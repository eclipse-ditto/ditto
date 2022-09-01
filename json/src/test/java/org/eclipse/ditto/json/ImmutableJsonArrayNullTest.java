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
package org.eclipse.ditto.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.junit.Test;

/**
 * Unit test for {@link ImmutableJsonArrayNull}.
 */
public final class ImmutableJsonArrayNullTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableJsonArrayNull.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        final ImmutableJsonArrayNull green = ImmutableJsonArrayNull.getInstance();
        final ImmutableJsonArrayNull blue = ImmutableJsonArrayNull.getInstance();

        assertThat(green)
                .isEqualTo(green)
                .isEqualTo(blue);

        final ImmutableJsonObjectNull nullObject = ImmutableJsonObjectNull.getInstance();

        assertThat(green.equals(nullObject)).isTrue();
    }

    @Test
    public void toStringReturnsExpected() {
        final ImmutableJsonArrayNull underTest = ImmutableJsonArrayNull.getInstance();

        assertThat(underTest.toString()).hasToString("null");
    }

}
