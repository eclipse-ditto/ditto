/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.tracing.instruments.trace;

import org.eclipse.ditto.internal.utils.metrics.instruments.TaggedMetricInstrument;
import org.eclipse.ditto.internal.utils.metrics.instruments.timer.StartInstant;
import org.eclipse.ditto.internal.utils.metrics.instruments.timer.StartedTimer;

/**
 * A trace which is prepared to be {@link #start() started}.
 */
public interface PreparedTrace extends TaggedMetricInstrument<PreparedTrace>, TraceTags<PreparedTrace> {

    @Override
    default PreparedTrace self() {
        return this;
    }

    /**
     * Starts the trace at the current instant.
     *
     * @return the StartedTrace.
     */
    StartedTrace start();

    /**
     * Starts the trace at the specified {@code StartedTrace} argument.
     *
     * @param startInstant the instant that determines when to start the trace.
     * @return the StartedTrace.
     * @throws NullPointerException if {@code startInstant} is {@code null}.
     */
    StartedTrace startAt(StartInstant startInstant);

    /**
     * Starts the trace at the same instant as the specified StartedTimer argument.
     * After the specified StartedTimer stopped, the returned trace automatically finishes after the duration of the
     * stopped timer and gets the tags of the stopped timer put to its own tags.
     *
     * @param startedTimer provides the name, the start and the finish time of the returned trace.
     * @return the new started trace which will be automatically stopped by {@code startedTimer}.
     * @throws NullPointerException if {@code startedTimer} is {@code null}.
     */
    StartedTrace startBy(StartedTimer startedTimer);

}
