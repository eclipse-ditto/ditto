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
package org.eclipse.ditto.internal.utils.metrics.instruments.timer;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kamon.Kamon;
import kamon.context.Context;
import kamon.tag.TagSet;
import kamon.trace.Span;

/**
 * Kamon based implementation of {@link StoppedTimer}.
 */
final class StoppedKamonTimer implements StoppedTimer {

    private static final String SEGMENT_TAG = "segment";
    private static final Logger LOGGER = LoggerFactory.getLogger(StoppedKamonTimer.class);

    private final String name;
    private final Map<String, String> tags;

    private final long startTimestamp;
    private final long endTimestamp;

    private StoppedKamonTimer(final String name, final Map<String, String> tags, final long startTimestamp,
            final Map<String, StartedTimer> segments, List<OnStopHandler> onStopHandlers, final Context context) {
        this.startTimestamp = startTimestamp;
        this.endTimestamp = System.nanoTime();
        this.name = name;
        this.tags = new HashMap<>(tags);
        segments.forEach((segmentName, segment) -> {
            if (segment.isRunning()) {
                segment.stop();
            }
        });

        final Span span = context.get(Span.Key());
        span.tag(TagSet.from(Map.copyOf(tags))).finishAfter(getDuration());

        final long durationNano = getElapsedNano();
        LOGGER.trace("Timer with name <{}> and segment <{}> was stopped after <{}> nanoseconds", name,
                tags.get(SEGMENT_TAG), durationNano);
        onStopHandlers.forEach(stoppedTimerConsumer -> stoppedTimerConsumer.handleStoppedTimer(this));
        getKamonInternalTimer().record(durationNano);
    }

    static StoppedTimer fromStartedTimer(final StartedTimer startedTimer) {
        return new StoppedKamonTimer(startedTimer.getName(), startedTimer.getTags(), startedTimer.getStartTimeStamp(),
                startedTimer.getSegments(), startedTimer.getOnStopHandlers(), startedTimer.getContext());
    }

    @Override
    public Duration getDuration() {
        return Duration.ofNanos(getElapsedNano());
    }

    @Override
    public Map<String, String> getTags() {
        return new HashMap<>(tags);
    }

    @Nullable
    @Override
    public String getTag(final String key) {
        return tags.get(key);
    }


    kamon.metric.Timer getKamonInternalTimer() {
        return Kamon.timer(name).withTags(TagSet.from(new HashMap<>(this.tags)));
    }

    @Override
    public String getName() {
        return this.name;
    }


    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "name=" + name +
                ", tags=" + tags +
                ", startTimestamp=" + startTimestamp +
                ", endTimestamp=" + endTimestamp +
                "]";
    }

    private long getElapsedNano() {
        return this.endTimestamp - this.startTimestamp;
    }
}
