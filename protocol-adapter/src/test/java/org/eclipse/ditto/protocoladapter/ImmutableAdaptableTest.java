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
package org.eclipse.ditto.protocoladapter;

import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableAdaptable}.
 */
public final class ImmutableAdaptableTest {

    /** */
    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableAdaptable.class)
                .usingGetClass()
                .verify();
    }

    /** */
    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableAdaptable.class,
                areImmutable(),
                provided(TopicPath.class, Payload.class, DittoHeaders.class).areAlsoImmutable());
    }

    /** */
    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullTopicPath() {
        ImmutableAdaptable.of(null, null, null);
    }

}
