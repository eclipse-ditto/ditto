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
package org.eclipse.ditto.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.junit.Test;
import org.mockito.Mockito;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableAdaptable}.
 */
public final class ImmutableAdaptableTest {

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableAdaptable.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableAdaptable.class,
                areImmutable(),
                provided(TopicPath.class, Payload.class, DittoHeaders.class).areAlsoImmutable());
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullTopicPath() {
        ImmutableAdaptable.of(null, null, null);
    }

    @Test
    public void getDittoHeadersReturnsEmptyHeadersIfNoneWereSet() {
        final ImmutableAdaptable underTest =
                ImmutableAdaptable.of(Mockito.mock(TopicPath.class), Mockito.mock(Payload.class), null);

        assertThat(underTest.getDittoHeaders()).isEmpty();
    }

}
