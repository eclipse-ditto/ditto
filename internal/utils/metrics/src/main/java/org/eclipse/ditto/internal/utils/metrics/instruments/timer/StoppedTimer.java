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
import java.util.Map;

import javax.annotation.Nullable;

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
     * @return All tags of this timer.
     */
    Map<String, String> getTags();

    /**
     * Gets the tag with the given key.
     *
     * @param key the key of the tag.
     * @return The value of the tag with the given key.
     */
    @Nullable
    String getTag(String key);
}
