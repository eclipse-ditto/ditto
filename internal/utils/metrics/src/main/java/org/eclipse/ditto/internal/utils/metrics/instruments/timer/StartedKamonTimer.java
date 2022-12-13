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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.eclipse.ditto.internal.utils.metrics.instruments.tag.Tag;
import org.eclipse.ditto.internal.utils.metrics.instruments.tag.TagSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Kamon based implementation of {@link StartedTimer}.
 */
final class StartedKamonTimer implements StartedTimer {

    private static final Logger LOGGER = LoggerFactory.getLogger(StartedKamonTimer.class);

    private static final String SEGMENT_TAG_KEY = "segment";

    private final String name;
    private final Map<String, StartedTimer> segments;
    private final List<OnStopHandler> onStopHandlers;
    private final StartInstant startInstant;

    private TagSet tags;
    @Nullable private StoppedTimer stoppedTimer;

    private StartedKamonTimer(final String name, final TagSet tags) {
        this.name = name;
        segments = new HashMap<>();
        onStopHandlers = new ArrayList<>();
        startInstant = StartInstant.now();
        this.tags = tags;
        stoppedTimer = null;
    }

    static StartedTimer fromPreparedTimer(final PreparedTimer preparedTimer) {
        final var result = new StartedKamonTimer(preparedTimer.getName(), preparedTimer.getTagSet());
        result.addOverallSegmentTagIfNoOtherSegmentTagExists();
        return result;
    }

    private void addOverallSegmentTagIfNoOtherSegmentTagExists() {
        if (!hasSegmentTag()) {
            tag(SEGMENT_TAG_KEY, "overall");
        }
    }

    private boolean hasSegmentTag() {
        return tags.containsKey(SEGMENT_TAG_KEY);
    }

    @Override
    public StartedTimer tags(final TagSet tags) {
        if (isStopped()) {
            LOGGER.warn("Tried to append multiple tags to the stopped timer with name <{}>. Tags are ineffective.",
                    name);
        } else {
            this.tags = tags.putAllTags(tags);
        }
        return this;
    }

    @Override
    public TagSet getTagSet() {
        return tags;
    }

    @Override
    public StartedTimer tag(final Tag tag) {
        checkNotNull(tag, "tag");
        if (isStopped()) {
            LOGGER.warn("Tried to append tag <{}> to the stopped timer with name <{}>. Tag is ineffective.", tag, name);
        } else {
            tags = tags.putTag(tag);
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
                    tags.getTagValue(SEGMENT_TAG_KEY).orElse(null));
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
                    .tag(SEGMENT_TAG_KEY, segmentName)
                    .start();
            segments.put(segmentName, result);
        } else {
            if (LOGGER.isWarnEnabled()) {
                LOGGER.warn(
                        "Tried to start a new segment <{}> on a already stopped timer <{}> with segment <{}>.",
                        segmentName,
                        name,
                        tags.getTagValue(SEGMENT_TAG_KEY).orElse(null)
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
