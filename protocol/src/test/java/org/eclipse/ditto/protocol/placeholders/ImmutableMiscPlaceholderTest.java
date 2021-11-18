/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.protocol.placeholders;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.assertj.core.data.TemporalUnitLessThanOffset;
import org.junit.Test;
import org.mutabilitydetector.unittesting.MutabilityAssert;
import org.mutabilitydetector.unittesting.MutabilityMatchers;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

/**
 * Tests {@link ImmutableMiscPlaceholder}.
 */
public final class ImmutableMiscPlaceholderTest {

    private static final ImmutableMiscPlaceholder UNDER_TEST = ImmutableMiscPlaceholder.INSTANCE;
    private static final Object SOME_OBJECT = new Object();

    @Test
    public void assertImmutability() {
        MutabilityAssert.assertInstancesOf(ImmutableMiscPlaceholder.class, MutabilityMatchers.areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableMiscPlaceholder.class)
                .suppress(Warning.INHERITED_DIRECTLY_FROM_OBJECT)
                .usingGetClass()
                .verify();
    }

    @Test
    public void testReplaceCurrentTimestampIso() {
        assertThat(UNDER_TEST.resolve(SOME_OBJECT, "current-timestamp-iso8601"))
                .map(Instant::parse)
                .hasValueSatisfying(i -> {
                            final Instant now = Instant.now();
                            assertThat(i)
                                    .isBefore(now)
                                    .isCloseTo(now, new TemporalUnitLessThanOffset(1000, ChronoUnit.MILLIS));
                        }
                );
    }

    @Test
    public void testReplaceCurrentTimestampMillisSinceEpoch() {
        assertThat(UNDER_TEST.resolve(SOME_OBJECT, "current-timestamp-epoch-millis"))
                .map(Long::parseLong)
                .map(Instant::ofEpochMilli)
                .hasValueSatisfying(i -> {
                            final Instant now = Instant.now();
                            assertThat(i)
                                    .isBefore(now)
                                    .isCloseTo(now, new TemporalUnitLessThanOffset(1000, ChronoUnit.MILLIS));
                        }
                );
    }

}
