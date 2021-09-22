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

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kamon.Kamon;

/**
 * Kamon based implementation of {@link StartedTimer}.
 */
final class StartedKamonTimer implements StartedTimer {

    private static final String SEGMENT_TAG = "segment";
    private static final Logger LOGGER = LoggerFactory.getLogger(StartedKamonTimer.class);

    private final String name;
    private final Map<String, String> tags;
    private final List<OnStopHandler> onStopHandlers;
    private final Map<String, StartedTimer> segments;
    private final long startNanoTime;
    private final Instant startInstant;

    @Nullable private StoppedTimer stoppedTimer;

    private StartedKamonTimer(final String name, final Map<String, String> tags) {
        this.name = name;
        this.tags = new HashMap<>(tags);
        this.segments = new HashMap<>();
        this.onStopHandlers = new ArrayList<>();
        this.stoppedTimer = null;
        this.startNanoTime = System.nanoTime();
        this.startInstant = Kamon.clock().toInstant(startNanoTime);

        if (!this.tags.containsKey(SEGMENT_TAG)) {
            tag(SEGMENT_TAG, "overall");
        }
    }

    static StartedTimer fromPreparedTimer(final PreparedTimer preparedTimer) {
        return new StartedKamonTimer(preparedTimer.getName(), preparedTimer.getTags());
    }

    @Override
    public StartedTimer tags(final Map<String, String> tags) {
        if (isStopped()) {
            LOGGER.warn("Tried to append multiple tags to the stopped timer with name <{}>. Tags are ineffective.",
                    name);
        } else {
            this.tags.putAll(tags);
        }
        return this;
    }

    @Nullable
    @Override
    public String getTag(final String key) {
        return tags.get(key);
    }

    @Override
    public Map<String, String> getTags() {
        return new HashMap<>(tags);
    }

    @Override
    public StartedTimer tag(final String key, final String value) {
        if (isStopped()) {
            LOGGER.warn(
                    "Tried to append tag <{}> with value <{}> to the stopped timer with name <{}>. Tag is ineffective.",
                    key, value, name);
        } else {
            this.tags.put(key, value);
        }
        return this;
    }

    @Override
    public StoppedTimer stop() {

        if (isRunning()) {
            stoppedTimer = StoppedKamonTimer.fromStartedTimer(this);
        }else {
            LOGGER.warn("Tried to stop the already stopped timer <{}> with segment <{}>.", name, getTag(SEGMENT_TAG));
        }
        return stoppedTimer;
    }

    @Override
    public boolean isRunning() {
        return stoppedTimer == null;
    }

    private boolean isStopped() {
        return !isRunning();
    }

    @Override
    public StartedTimer startNewSegment(final String segmentName) {
        if (isRunning()) {
            final StartedTimer segment = PreparedKamonTimer.newTimer(name)
                    .tags(tags)
                    .tag(SEGMENT_TAG, segmentName)
                    .start();
            this.segments.put(segmentName, segment);
            return segment;
        } else {
            LOGGER.warn("Tried to start a new segment <{}> on a already stopped timer <{}> with segment <{}>.",
                    segmentName, name, getTag(SEGMENT_TAG));
            return this;
        }
    }

    @Override
    public StartedTimer onStop(final OnStopHandler onStopHandler) {
        onStopHandlers.add(onStopHandler);
        return this;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public Instant getStartInstant() {
        return startInstant;
    }

    @Override
    public Long getStartNanoTime() {
        return startNanoTime;
    }

    @Override
    public Map<String, StartedTimer> getSegments() {
        return new HashMap<>(segments);
    }

    @Override
    public List<OnStopHandler> getOnStopHandlers() {
        return List.copyOf(onStopHandlers);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "name=" + name +
                ", tags=" + tags +
                ", onStopHandlers=" + onStopHandlers +
                ", segments=" + segments +
                ", startNanoTime=" + startNanoTime +
                ", startInstant=" + startInstant +
                ", stoppedTimer=" + stoppedTimer +
                "]";
    }

}
