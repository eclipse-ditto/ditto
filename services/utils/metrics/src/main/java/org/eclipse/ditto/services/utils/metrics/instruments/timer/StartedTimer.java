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

import java.util.List;
import java.util.Map;

import org.eclipse.ditto.services.utils.metrics.instruments.TaggedMetricInstrument;

/**
 * A started Timer metric. New instances are always built as started timers. No manual start is possible/required.
 */
public interface StartedTimer extends Timer, TaggedMetricInstrument<StartedTimer> {

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
     * Returns the start timestamp in nanos.
     *
     * @return The start timestamp in nanos.
     */
    Long getStartTimeStamp();

    /**
     * Gets all segments of this timer.
     *
     * @return Segments of this timer.
     */
    Map<String, StartedTimer> getSegments();

    /**
     * Gets all on stop handlers of this timer.
     *
     * @return All on stop handlers of this timer.
     */
    List<OnStopHandler> getOnStopHandlers();
}
