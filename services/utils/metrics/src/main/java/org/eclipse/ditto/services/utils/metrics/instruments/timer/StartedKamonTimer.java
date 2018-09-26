/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.utils.metrics.instruments.timer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Kamon based implementation of {@link StartedTimer}.
 */
public class StartedKamonTimer implements StartedTimer {


    private static final String SEGMENT_TAG = "segment";
    private static final Logger LOGGER = LoggerFactory.getLogger(StartedKamonTimer.class);

    private final String name;
    private final Map<String, String> tags;
    private final List<OnStopHandler> onStopHandlers;
    private final Map<String, StartedTimer> segments;
    private final long startTimestamp;

    private boolean stopped;

    private StartedKamonTimer(final String name, final Map<String, String> tags) {
        this.name = name;
        this.tags = new HashMap<>(tags);
        this.segments = new HashMap<>();
        this.onStopHandlers = new ArrayList<>();
        this.stopped = false;
        this.startTimestamp = System.nanoTime();
        if (!this.tags.containsKey(SEGMENT_TAG)) {tag(SEGMENT_TAG, "overall");}
    }

    static StartedTimer fromPreparedTimer(final PreparedTimer preparedTimer) {
        return new StartedKamonTimer(preparedTimer.getName(), preparedTimer.getTags());
    }

    @Override
    public StartedTimer tags(final Map<String, String> tags) {
        if (stopped) {
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
        if (stopped) {
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
            stopped = true;
            return StoppedKamonTimer.fromStartedTimer(this);
        }

        throw new IllegalStateException(
                String.format("Tried to stop the already stopped timer <%s> with segment <%s>.", name,
                        getTag(SEGMENT_TAG)));
    }

    @Override
    public boolean isRunning() {
        return !stopped;
    }

    @Override
    public StartedTimer startNewSegment(final String segmentName) {
        verifyRunning();
        final StartedTimer segment = PreparedKamonTimer.newTimer(name)
                .tags(this.tags)
                .tag(SEGMENT_TAG, segmentName)
                .start();
        this.segments.put(segmentName, segment);
        return segment;
    }

    @Override
    public StartedTimer onStop(final OnStopHandler onStopHandler) {
        onStopHandlers.add(onStopHandler);
        return this;
    }

    private void verifyRunning() {
        if (!isRunning()) {
            throw new IllegalStateException("Timer has been stopped, already.");
        }
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public Long getStartTimeStamp() {
        return startTimestamp;
    }

    @Override
    public Map<String, StartedTimer> getSegments() {
        return new HashMap<>(segments);
    }

    @Override
    public List<OnStopHandler> getOnStopHandlers() {
        return new ArrayList<>(onStopHandlers);
    }


    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "name=" + name +
                ", tags=" + tags +
                ", onStopHandlers=" + onStopHandlers +
                ", segments=" + segments +
                ", startTimestamp=" + startTimestamp +
                ", stopped=" + stopped +
                "]";
    }
}
