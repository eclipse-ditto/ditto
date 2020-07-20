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

import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableLabel}.
 */
public final class ImmutableLabelTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableLabel.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableLabel.class)
                .usingGetClass()
                .verify();
    }

    @Test(expected = PolicyEntryInvalidException.class)
    public void createInvalidAttribute() {
        final String invalidLabel = "invalidLabel/";
        ImmutableLabel.of(invalidLabel);
    }

    @Test(expected = PolicyEntryInvalidException.class)
    public void createTooLargeAttribute() {
        final String tooLongLabel = generateMaximumLength().append("a").toString();
        ImmutableLabel.of(tooLongLabel);
    }

    private StringBuilder generateMaximumLength() {
        final int maxLength = 256;
        final StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < maxLength; i++) {
            stringBuilder.append("a");
        }
        return stringBuilder;
    }
}
