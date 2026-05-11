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
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.timeseries.model.TimeseriesDataPoint;
import org.eclipse.ditto.timeseries.model.TimeseriesQuery;
import org.eclipse.ditto.timeseries.model.TimeseriesQueryResult;
import org.junit.Test;

/**
 * Tests for the {@code default} method behaviour on {@link TimeseriesAdapter}.
 */
public final class TimeseriesAdapterDefaultsTest {

    private static final ThingId THING_ID = ThingId.of("org.eclipse.ditto", "sensor-1");
    private static final JsonPointer PATH = JsonPointer.of("/features/env/properties/temperature");

    private static TimeseriesDataPoint dataPoint(final int second) {
        return TimeseriesDataPoint.of(
                THING_ID,
                PATH,
                Instant.parse("2026-01-15T10:30:0" + second + "Z"),
                JsonValue.of(20 + second),
                42L + second,
                Collections.emptyMap(),
                null);
    }

    @Test
    public void defaultWriteBatchInvokesWriteForEachDataPointInOrder() {
        final RecordingAdapter adapter = new RecordingAdapter();
        final List<TimeseriesDataPoint> batch = Arrays.asList(dataPoint(0), dataPoint(1), dataPoint(2));

        adapter.writeBatch(batch).toCompletableFuture().join();

        assertThat(adapter.recorded).containsExactlyElementsOf(batch);
    }

    @Test
    public void defaultWriteBatchAcceptsEmptyList() {
        final RecordingAdapter adapter = new RecordingAdapter();

        adapter.writeBatch(Collections.emptyList()).toCompletableFuture().join();

        assertThat(adapter.recorded).isEmpty();
    }

    @Test
    public void defaultWriteBatchRejectsNullList() {
        final RecordingAdapter adapter = new RecordingAdapter();

        assertThatNullPointerException().isThrownBy(() -> adapter.writeBatch(null));
    }

    @Test
    public void defaultWriteBatchRejectsNullElement() {
        final RecordingAdapter adapter = new RecordingAdapter();
        final List<TimeseriesDataPoint> batchWithNull = Arrays.asList(dataPoint(0), null);

        assertThatNullPointerException().isThrownBy(() -> adapter.writeBatch(batchWithNull));
    }

    /**
     * Minimal in-memory adapter that records every data point passed to {@code write}, for
     * verifying the default-method behaviour of {@link TimeseriesAdapter}.
     */
    private static final class RecordingAdapter implements TimeseriesAdapter {

        private final List<TimeseriesDataPoint> recorded = new ArrayList<>();

        @Override
        public CompletionStage<Void> initialize(final TimeseriesAdapterConfig config) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> shutdown() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public HealthStatus getHealth() {
            return HealthStatus.UP;
        }

        @Override
        public CompletionStage<Void> write(final TimeseriesDataPoint dataPoint) {
            recorded.add(dataPoint);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<List<TimeseriesQueryResult>> query(final TimeseriesQuery query) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
    }
}
