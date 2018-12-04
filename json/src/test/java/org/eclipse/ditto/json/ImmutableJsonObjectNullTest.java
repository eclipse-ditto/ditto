/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.junit.Test;

/**
 * Unit test for {@link ImmutableJsonObjectNull}.
 */
public final class ImmutableJsonObjectNullTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableJsonObjectNull.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        final ImmutableJsonObjectNull green = ImmutableJsonObjectNull.getInstance();
        final ImmutableJsonObjectNull blue = ImmutableJsonObjectNull.getInstance();

        assertThat(green).isEqualTo(green);
        assertThat(green).isEqualTo(blue);

        assertThat(blue).isEqualTo(ImmutableJsonArrayNull.getInstance());
    }

    @Test
    public void toStringReturnsExpected() {
        final ImmutableJsonObjectNull underTest = ImmutableJsonObjectNull.getInstance();

        assertThat(underTest.toString()).isEqualTo("null");
    }

}
