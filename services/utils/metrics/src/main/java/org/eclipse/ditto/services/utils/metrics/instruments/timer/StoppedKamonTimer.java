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
package org.eclipse.ditto.services.utils.metrics.instruments.timer;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kamon.Kamon;

/**
 * Kamon based implementation of {@link StoppedTimer}.
 */
public class StoppedKamonTimer implements StoppedTimer {

    private static final String SEGMENT_TAG = "segment";
    private static final Logger LOGGER = LoggerFactory.getLogger(StoppedKamonTimer.class);

    private final String name;
    private final Map<String, String> tags;

    private long startTimestamp;
    private long endTimestamp;

    private StoppedKamonTimer(final String name, final Map<String, String> tags, final long startTimestamp,
            final Map<String, StartedTimer> segments, List<OnStopHandler> onStopHandlers) {
        this.startTimestamp = startTimestamp;
        this.endTimestamp = System.nanoTime();
        this.name = name;
        this.tags = new HashMap<>(tags);
        segments.forEach((segmentName, segment) -> {
            if (segment.isRunning()) {
                segment.stop();
            }
        });

        LOGGER.debug("Timer with name <{}> and segment <{}> was stopped after <{}> nanoseconds", name,
                tags.get(SEGMENT_TAG), getDuration());
        onStopHandlers
                .forEach(stoppedTimerConsumer -> stoppedTimerConsumer.handleStoppedTimer(this));
        getKamonInternalTimer().record(getDuration().getNano());
    }

    static StoppedTimer fromStartedTimer(final StartedTimer startedTimer) {

        return new StoppedKamonTimer(startedTimer.getName(), startedTimer.getTags(), startedTimer.getStartTimeStamp(),
                startedTimer.getSegments(), startedTimer.getOnStopHandlers());
    }

    @Override
    public Duration getDuration() {
        return Duration.ofNanos(this.endTimestamp - this.startTimestamp);
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


    private kamon.metric.Timer getKamonInternalTimer() {
        return Kamon.timer(name).refine(this.tags);
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
}
