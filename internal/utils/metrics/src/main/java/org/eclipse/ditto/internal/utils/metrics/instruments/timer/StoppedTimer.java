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

import org.eclipse.ditto.internal.utils.metrics.instruments.tag.TagSet;

/**
 * A stopped Timer metric.
 */
public interface StoppedTimer extends Timer {

    /**
     * Gets the duration.
     *
     * @return The duration.
     * @throws IllegalStateException if timer has not been started and stopped before calling this method.
     */
    Duration getDuration();

    /**
     * Gets all tags of this timer.
     *
     * @return all tags of this timer.
     */
    TagSet getTagSet();

}
