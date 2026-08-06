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
package org.eclipse.ditto.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

/**
 * Tests covering the {@link TopicPath.Criterion#TIMESERIES} enum value introduced for the
 * Timeseries API (GitHub issue #2291).
 * <p>
 * The full bi-directional protocol adapter (TimeseriesQueryAdapter, MappingStrategies,
 * SignalMapper, registration in DittoProtocolAdapter / DefaultAdapterResolver /
 * AdapterResolverBySignal, and a dedicated TimeseriesTopicPathBuilder) lands in a separate
 * commit alongside the {@code add-protocol-adapter.md} discipline checklist; this test pins
 * the enum addition itself.
 */
public final class TopicPathCriterionTimeseriesTest {

    @Test
    public void timeseriesCriterionWireFormatIsLowercase() {
        assertThat(TopicPath.Criterion.TIMESERIES.getName()).isEqualTo("timeseries");
    }

    @Test
    public void forNameResolvesTimeseries() {
        assertThat(TopicPath.Criterion.forName("timeseries"))
                .contains(TopicPath.Criterion.TIMESERIES);
    }

    @Test
    public void forNameIsCaseSensitive() {
        assertThat(TopicPath.Criterion.forName("TIMESERIES")).isEmpty();
    }

    @Test
    public void timeseriesIsAmongValues() {
        assertThat(TopicPath.Criterion.values()).contains(TopicPath.Criterion.TIMESERIES);
    }

    @Test
    public void retrieveActionAlreadyExistsForTimeseriesUse() {
        // The concept document's topic path <ns>/<name>/things/twin/timeseries/retrieve reuses
        // the existing RETRIEVE action — verify it remains available so the protocol adapter
        // can reuse it.
        assertThat(TopicPath.Action.forName("retrieve")).contains(TopicPath.Action.RETRIEVE);
    }
}
