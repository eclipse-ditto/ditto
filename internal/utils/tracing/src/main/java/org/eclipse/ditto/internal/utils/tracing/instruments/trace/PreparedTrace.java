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

import java.util.function.Consumer;
import java.util.function.Function;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.metrics.instruments.TaggedMetricInstrument;

/**
 * A trace which is prepared to be {@link #start() started}.
 */
public interface PreparedTrace extends TaggedMetricInstrument<PreparedTrace>, TraceTags<PreparedTrace> {

    /**
     * Starts the trace.
     *
     * @return The started {@link StartedTrace trace}.
     */
    StartedTrace start();

    /**
     * Executes the given {@code Function} and records the duration with this {@code PreparedTrace} .
     *
     * @param function the function to execute
     * @param <T> the return type of the supplier
     * @return the value returned by the executed Supplier
     */
    <T> T run(DittoHeaders dittoHeaders, Function<DittoHeaders, T> function);

    /**
     * Executes the given {@code Consumer} and records the duration with this {@code PreparedTrace} .
     *
     * @param consumer the consumer to execute
     */
    void run(DittoHeaders dittoHeaders, Consumer<DittoHeaders> consumer);

}
