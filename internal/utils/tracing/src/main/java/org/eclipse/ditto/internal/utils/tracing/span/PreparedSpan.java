/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.tracing.span;

import org.eclipse.ditto.internal.utils.metrics.instruments.TaggedMetricInstrument;
import org.eclipse.ditto.internal.utils.metrics.instruments.timer.StartInstant;
import org.eclipse.ditto.internal.utils.metrics.instruments.timer.StartedTimer;

/**
 * A tracing span which is prepared to be {@link #start() started}.
 */
public interface PreparedSpan extends TaggedMetricInstrument<PreparedSpan>, SpanTagging<PreparedSpan> {

    @Override
    default PreparedSpan self() {
        return this;
    }

    /**
     * Starts this span at the current instant.
     *
     * @return the StartedSpan.
     */
    StartedSpan start();

    /**
     * Starts this span at the specified {@code StartedSpan} argument.
     *
     * @param startInstant the instant that determines when to start this span.
     * @return the StartedSpan.
     * @throws NullPointerException if {@code startInstant} is {@code null}.
     */
    StartedSpan startAt(StartInstant startInstant);

    /**
     * Starts this span at the same instant as the specified StartedTimer argument.
     * After the specified StartedTimer stopped, the returned span automatically finishes after the duration of the
     * stopped timer and gets the tags of the stopped timer put to its own tags.
     *
     * @param startedTimer provides the name, the start and the finish time of the returned span.
     * @return the new started span which will be automatically stopped by {@code startedTimer}.
     * @throws NullPointerException if {@code startedTimer} is {@code null}.
     */
    StartedSpan startBy(StartedTimer startedTimer);

}
