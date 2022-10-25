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

import org.eclipse.ditto.internal.utils.metrics.instruments.tag.KamonTagSetConverter;
import org.eclipse.ditto.internal.utils.metrics.instruments.tag.TagSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kamon.Kamon;

/**
 * Kamon based implementation of {@link StoppedTimer}.
 */
final class StoppedKamonTimer implements StoppedTimer {

    private static final String SEGMENT_TAG = "segment";
    private static final Logger LOGGER = LoggerFactory.getLogger(StoppedKamonTimer.class);

    private final String name;
    private final StartInstant startInstant;
    private final long endNanoTime;
    private final TagSet tags;

    private StoppedKamonTimer(final StartedTimer startedTimer) {
        name = startedTimer.getName();
        startInstant = startedTimer.getStartInstant();
        endNanoTime = System.nanoTime();
        tags = startedTimer.getTagSet();
        startedTimer.getSegments().forEach((segmentName, segment) -> {
            if (segment.isRunning()) {
                segment.stop();
            }
        });

        final var durationNano = getElapsedNano();
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Timer with name <{}> and segment <{}> was stopped after <{}> nanoseconds",
                    name,
                    tags.getTagValue(SEGMENT_TAG).orElse(null),
                    durationNano);
        }
        startedTimer.getOnStopHandlers().forEach(stoppedTimerConsumer -> stoppedTimerConsumer.handleStoppedTimer(this));
        getKamonInternalTimer().record(durationNano);
    }

    static StoppedTimer fromStartedTimer(final StartedTimer startedTimer) {
        return new StoppedKamonTimer(startedTimer);
    }

    @Override
    public Duration getDuration() {
        return Duration.ofNanos(getElapsedNano());
    }

    @Override
    public TagSet getTagSet() {
        return tags;
    }

    kamon.metric.Timer getKamonInternalTimer() {
        return Kamon.timer(name).withTags(KamonTagSetConverter.getKamonTagSet(tags));
    }

    @Override
    public String getName() {
        return name;
    }

    private long getElapsedNano() {
        return endNanoTime - startInstant.toNanos();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "name=" + name +
                ", tags=" + tags +
                ", startNanoTime=" + startInstant.toNanos() +
                ", endNanoTime=" + endNanoTime +
                "]";
    }

}
