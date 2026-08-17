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
 * Unit tests for {@link FillStrategy}.
 */
public final class FillStrategyTest {

    @Test
    public void wireFormatNamesUseLowercaseTokens() {
        assertThat(FillStrategy.NULL.getName()).isEqualTo("null");
        assertThat(FillStrategy.PREVIOUS.getName()).isEqualTo("previous");
        assertThat(FillStrategy.LINEAR.getName()).isEqualTo("linear");
        assertThat(FillStrategy.ZERO.getName()).isEqualTo("zero");
    }

    @Test
    public void toStringReturnsWireFormatName() {
        assertThat(FillStrategy.PREVIOUS.toString()).isEqualTo("previous");
    }

    @Test
    public void forNameMatchesWireFormat() {
        assertThat(FillStrategy.forName("null")).contains(FillStrategy.NULL);
        assertThat(FillStrategy.forName("previous")).contains(FillStrategy.PREVIOUS);
        assertThat(FillStrategy.forName("linear")).contains(FillStrategy.LINEAR);
        assertThat(FillStrategy.forName("zero")).contains(FillStrategy.ZERO);
    }

    @Test
    public void forNameIsCaseSensitive() {
        assertThat(FillStrategy.forName("NULL")).isEmpty();
        assertThat(FillStrategy.forName("Previous")).isEmpty();
    }

    @Test
    public void forNameReturnsEmptyForUnknownToken() {
        assertThat(FillStrategy.forName("backfill")).isEmpty();
        assertThat(FillStrategy.forName("")).isEmpty();
    }

    @Test
    public void forNameRejectsNullInput() {
        assertThatNullPointerException().isThrownBy(() -> FillStrategy.forName(null));
    }

    @Test
    public void charSequenceContractDelegatesToName() {
        final FillStrategy underTest = FillStrategy.LINEAR;

        assertThat(underTest.length()).isEqualTo("linear".length());
        assertThat(underTest.charAt(0)).isEqualTo('l');
        assertThat(underTest.subSequence(0, 3)).isEqualTo("lin");
    }

    @Test
    public void allWireFormatNamesAreUnique() {
        final long distinct = java.util.Arrays.stream(FillStrategy.values())
                .map(FillStrategy::getName)
                .distinct()
                .count();
        assertThat(distinct).isEqualTo(FillStrategy.values().length);
    }

    @Test
    public void everyEnumValueResolvesViaForName() {
        for (final FillStrategy strategy : FillStrategy.values()) {
            final Optional<FillStrategy> resolved = FillStrategy.forName(strategy.getName());
            assertThat(resolved).contains(strategy);
        }
    }
}
