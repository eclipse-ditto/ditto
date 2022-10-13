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

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.eclipse.ditto.internal.utils.metrics.instruments.TaggableMetricsInstrument;
import org.eclipse.ditto.internal.utils.tracing.TraceOperationName;

/**
 * A started trace.
 */
public interface StartedTrace extends TaggableMetricsInstrument<StartedTrace>, TraceTags<StartedTrace> {

    @Override
    default StartedTrace self() {
        return this;
    }

    /**
     * Finishes the trace. Any method called after the trace is finished has no effect.
     */
    void finish();

    /**
     * Finishes the trace with the given duration. Any method called after the trace is finished has no effect.
     *
     * @param duration the duration after which to finish the trace
     */
    void finishAfter(Duration duration);

    /**
     * Marks the current operation as failed and adds the provided error message as a tag.
     *
     * @param errorMessage message describing the error
     * @return this trace
     */
    StartedTrace fail(String errorMessage);

    /**
     * Marks the current operation as failed and optionally adds the provided error stack trace as a tag. See the
     * "kamon.trace.include-error-stacktrace" setting for more information.
     *
     * @param throwable the throwable
     * @return this trace
     */
    StartedTrace fail(Throwable throwable);

    /**
     * Marks the current operation as failed and adds the provided error message as a tag and optionally adds the
     * provided error stack trace as a tag.
     *
     * @param errorMessage message describing the error
     * @param throwable the throwable
     * @return this trace
     */
    StartedTrace fail(String errorMessage, Throwable throwable);

    /**
     * Adds a new mark with the provided key using the current instant.
     *
     * @param key the key of the created mark
     * @return this trace
     */
    StartedTrace mark(String key);

    /**
     * Adds a new mark with the provided key using the provided instant.
     *
     * @param key the key of the created mark
     * @param at the provided instant
     * @return this trace
     */
    StartedTrace mark(String key, Instant at);

    /**
     * Returns the identifier of this trace span.
     *
     * @return the identifier.
     */
    SpanId getSpanId();

    /**
     * Returns the operation name of this trace span.
     *
     * @return the operation name.
     */
    TraceOperationName getOperationName();

    /**
     * Propagates the current context of this trace to the specified map.
     *
     * @param map the map to which the current trace context is propagated.
     * @return the given map with the current trace context added.
     * @throws NullPointerException if {@code map} is {@code null}.
     */
    Map<String, String> propagateContext(Map<String, String> map);

    /**
     * Spawns a child trace of this StartedTrace with the specified TraceOperationName argument.
     *
     * @param traceOperationName the operation name of the returned child trace.
     * @return the child trace of this trace.
     * @throws NullPointerException if {@code traceOperationName} is {@code null}.
     */
    PreparedTrace spawnChild(TraceOperationName traceOperationName);

}
