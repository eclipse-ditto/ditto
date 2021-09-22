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

import java.time.Instant;
import java.util.function.Consumer;
import java.util.function.Function;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.metrics.instruments.TaggedMetricInstrument;

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
     * @return The started {@link StartedTrace trace}.
     */
    StartedTrace start();

    /**
     * Starts the trace setting the given start instant.
     * <p>
     * The passed in {@code Instant} <strong>must be</strong> created using Kamon's clock {@link kamon.Kamon#clock()},
     * a "normal" Instant of the JVM might have too low precision or might be too slow.
     * </p>
     *
     * @param startInstant the instant where to start the trace at.
     * @return The started {@link StartedTrace trace}.
     */
    StartedTrace startAt(Instant startInstant);

    /**
     * Executes the given {@code Function} and records the duration with this {@code PreparedTrace}.
     *
     * @param dittoHeaders the DittoHeaders to which the trace context is attached
     * @param function the function to execute
     * @param <T> the return type of the function
     * @return the value returned by the executed Function
     */
    <T> T run(DittoHeaders dittoHeaders, Function<DittoHeaders, T> function);

    /**
     * Executes the given {@code Consumer} and records the duration with this {@code PreparedTrace}.
     *
     * @param dittoHeaders the DittoHeaders to which the trace context is attached
     * @param consumer the consumer to execute
     */
    void run(DittoHeaders dittoHeaders, Consumer<DittoHeaders> consumer);

}
