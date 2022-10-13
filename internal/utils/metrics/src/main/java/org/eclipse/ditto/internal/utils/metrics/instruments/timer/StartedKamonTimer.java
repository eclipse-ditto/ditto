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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private final StartInstant startInstant;

    @Nullable private StoppedTimer stoppedTimer;

    private StartedKamonTimer(final String name, final Map<String, String> tags) {
        this.name = name;
        this.tags = new HashMap<>(tags);
        segments = new HashMap<>();
        onStopHandlers = new ArrayList<>();
        stoppedTimer = null;
        startInstant = StartInstant.now();

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

    @Override
    public Optional<String> getTag(final String key) {
        return Optional.ofNullable(tags.get(key));
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
            tags.put(key, value);
        }
        return this;
    }

    @Override
    public StoppedTimer stop() {

        if (isRunning()) {
            stoppedTimer = StoppedKamonTimer.fromStartedTimer(this);
        } else if (LOGGER.isWarnEnabled()) {
            LOGGER.warn("Tried to stop the already stopped timer <{}> with segment <{}>.",
                    name,
                    getTag(SEGMENT_TAG).orElse(null));
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
        final StartedTimer result;
        if (isRunning()) {
            result = PreparedKamonTimer.newTimer(name)
                    .tags(tags)
                    .tag(SEGMENT_TAG, segmentName)
                    .start();
            segments.put(segmentName, result);
        } else {
            if (LOGGER.isWarnEnabled()) {
                LOGGER.warn(
                        "Tried to start a new segment <{}> on a already stopped timer <{}> with segment <{}>.",
                        segmentName,
                        name,
                        getTag(SEGMENT_TAG).orElse(null)
                );
            }
            result = this;
        }
        return result;
    }

    @Override
    public StartedTimer onStop(final OnStopHandler onStopHandler) {
        onStopHandlers.add(onStopHandler);
        return this;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public StartInstant getStartInstant() {
        return startInstant;
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
                ", startNanoTime=" + startInstant.toNanos() +
                ", startInstant=" + startInstant.toInstant() +
                ", stoppedTimer=" + stoppedTimer +
                "]";
    }

}
