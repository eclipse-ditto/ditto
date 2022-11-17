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

import java.util.List;
import java.util.Map;

import org.eclipse.ditto.internal.utils.metrics.instruments.TaggedMetricInstrument;

/**
 * A started Timer metric. New instances are always built as started timers. No manual start is possible/required.
 */
public interface StartedTimer extends Timer, TaggedMetricInstrument<StartedTimer> {

    @Override
    default StartedTimer self() {
        return this;
    }

    /**
     * Stops the timer and all its segments.
     *
     * @return The stopped timer.
     */
    StoppedTimer stop();

    /**
     * Indicates whether this timer is still running.
     *
     * @return True if running, False if not.
     */
    boolean isRunning();

    /**
     * Starts a new timer with the same name and the same tags as this timer but with an additional segment tag that
     * contains the given segment name.
     * This segment will be stopped when its parent (the timer you use to start a new segment) is stopped, if it's not
     * stopped before.
     *
     * @param segmentName The name that will be stored in the segment tag.
     * @return The started timer.
     */
    StartedTimer startNewSegment(String segmentName);

    /**
     * Registers the passed {@code onStopHandler} to be invoked when this timer stops.
     *
     * @param onStopHandler the handler to invoke when this timer stops.
     */
    StartedTimer onStop(OnStopHandler onStopHandler);

    /**
     * Gets all on stop handlers of this timer.
     *
     * @return All on stop handlers of this timer.
     */
    List<OnStopHandler> getOnStopHandlers();

    /**
     * @return the instant when the timer was started.
     */
    StartInstant getStartInstant();

    /**
     * Gets all segments of this timer.
     *
     * @return Segments of this timer.
     */
    Map<String, StartedTimer> getSegments();

}
