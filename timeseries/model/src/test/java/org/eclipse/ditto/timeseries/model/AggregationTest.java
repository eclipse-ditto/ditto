/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.timeseries.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Optional;

import org.junit.Test;

/**
 * Unit tests for {@link Aggregation}.
 */
public final class AggregationTest {

    @Test
    public void wireFormatNamesUseLowercaseTokens() {
        assertThat(Aggregation.AVG.getName()).isEqualTo("avg");
        assertThat(Aggregation.MIN.getName()).isEqualTo("min");
        assertThat(Aggregation.MAX.getName()).isEqualTo("max");
        assertThat(Aggregation.SUM.getName()).isEqualTo("sum");
        assertThat(Aggregation.COUNT.getName()).isEqualTo("count");
        assertThat(Aggregation.FIRST.getName()).isEqualTo("first");
        assertThat(Aggregation.LAST.getName()).isEqualTo("last");
        assertThat(Aggregation.DERIVATIVE.getName()).isEqualTo("derivative");
        assertThat(Aggregation.RATE.getName()).isEqualTo("rate");
        assertThat(Aggregation.INTEGRAL.getName()).isEqualTo("integral");
        assertThat(Aggregation.STDDEV.getName()).isEqualTo("stddev");
        assertThat(Aggregation.PERCENTILE.getName()).isEqualTo("percentile");
    }

    @Test
    public void toStringReturnsWireFormatName() {
        assertThat(Aggregation.AVG.toString()).isEqualTo("avg");
        assertThat(Aggregation.PERCENTILE.toString()).isEqualTo("percentile");
    }

    @Test
    public void forNameMatchesWireFormat() {
        assertThat(Aggregation.forName("avg")).contains(Aggregation.AVG);
        assertThat(Aggregation.forName("percentile")).contains(Aggregation.PERCENTILE);
    }

    @Test
    public void forNameIsCaseSensitive() {
        // Wire format is lowercase by contract; uppercase must NOT silently match.
        assertThat(Aggregation.forName("AVG")).isEmpty();
        assertThat(Aggregation.forName("Avg")).isEmpty();
    }

    @Test
    public void forNameReturnsEmptyForUnknownToken() {
        assertThat(Aggregation.forName("median")).isEmpty();
        assertThat(Aggregation.forName("")).isEmpty();
    }

    @Test
    public void forNameRejectsNullInput() {
        assertThatNullPointerException().isThrownBy(() -> Aggregation.forName(null));
    }

    @Test
    public void charSequenceContractDelegatesToName() {
        final Aggregation underTest = Aggregation.STDDEV;

        assertThat(underTest.length()).isEqualTo("stddev".length());
        assertThat(underTest.charAt(0)).isEqualTo('s');
        assertThat(underTest.subSequence(0, 3)).isEqualTo("std");
    }

    @Test
    public void allWireFormatNamesAreUnique() {
        final long distinct = java.util.Arrays.stream(Aggregation.values())
                .map(Aggregation::getName)
                .distinct()
                .count();
        assertThat(distinct).isEqualTo(Aggregation.values().length);
    }

    @Test
    public void everyEnumValueResolvesViaForName() {
        for (final Aggregation aggregation : Aggregation.values()) {
            final Optional<Aggregation> resolved = Aggregation.forName(aggregation.getName());
            assertThat(resolved).contains(aggregation);
        }
    }
}
