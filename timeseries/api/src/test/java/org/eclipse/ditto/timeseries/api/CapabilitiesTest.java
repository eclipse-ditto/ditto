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
package org.eclipse.ditto.timeseries.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.EnumSet;

import org.eclipse.ditto.timeseries.model.Aggregation;
import org.eclipse.ditto.timeseries.model.FillStrategy;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

/**
 * Unit tests for {@link Capabilities}.
 */
public final class CapabilitiesTest {

    @Test
    public void minimalPushesNothingDown() {
        final Capabilities minimal = Capabilities.minimal();

        assertThat(minimal.supportsNativeQuery()).isFalse();
        assertThat(minimal.getPushableAggregations()).isEmpty();
        assertThat(minimal.getNativeFillStrategies()).isEmpty();
        assertThat(minimal.canPushDown(Aggregation.AVG)).isFalse();
        assertThat(minimal.canFillNatively(FillStrategy.LINEAR)).isFalse();
    }

    @Test
    public void builderDeclaresCapabilities() {
        final Capabilities caps = Capabilities.builder()
                .supportsNativeQuery(true)
                .pushableAggregations(EnumSet.of(Aggregation.AVG, Aggregation.SUM))
                .nativeFillStrategies(EnumSet.of(FillStrategy.LINEAR))
                .build();

        assertThat(caps.supportsNativeQuery()).isTrue();
        assertThat(caps.canPushDown(Aggregation.AVG)).isTrue();
        assertThat(caps.canPushDown(Aggregation.SUM)).isTrue();
        assertThat(caps.canPushDown(Aggregation.MIN)).isFalse();
        assertThat(caps.canFillNatively(FillStrategy.LINEAR)).isTrue();
        assertThat(caps.canFillNatively(FillStrategy.PREVIOUS)).isFalse();
    }

    @Test
    public void declaredSetsAreDefensivelyCopied() {
        final EnumSet<Aggregation> aggs = EnumSet.of(Aggregation.AVG);
        final Capabilities caps = Capabilities.builder().pushableAggregations(aggs).build();

        aggs.add(Aggregation.MAX); // mutate the caller's set after build

        assertThat(caps.canPushDown(Aggregation.MAX)).isFalse();
    }

    @Test
    public void pushableAggregationsSetIsUnmodifiable() {
        final Capabilities caps = Capabilities.builder()
                .pushableAggregations(EnumSet.of(Aggregation.AVG))
                .build();

        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> caps.getPushableAggregations().add(Aggregation.MIN));
    }

    @Test
    public void equalsAndHashCode() {
        EqualsVerifier.forClass(Capabilities.class)
                .usingGetClass()
                .suppress(Warning.NULL_FIELDS) // sets are never null (constructor-enforced)
                .verify();
    }

    @Test
    public void canPushDownRejectsNull() {
        assertThatNullPointerException().isThrownBy(() -> Capabilities.minimal().canPushDown(null));
    }

    @Test
    public void builderRejectsNullSets() {
        assertThatNullPointerException()
                .isThrownBy(() -> Capabilities.builder().pushableAggregations(null));
        assertThatNullPointerException()
                .isThrownBy(() -> Capabilities.builder().nativeFillStrategies(null));
    }
}
