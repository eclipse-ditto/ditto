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

import org.eclipse.ditto.internal.utils.tracing.TraceOperationName;

import kamon.context.Context;

/**
 * Factory methods for traces.
 */
public final class Traces {

    private Traces() {
        throw new AssertionError();
    }

    /**
     * Builds a {@link PreparedTrace} with the given name.
     *
     * @param context the trace context.
     * @param operationName the name of the operation.
     * @return the new prepared trace.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static PreparedTrace newTrace(final Context context, final TraceOperationName operationName) {
        return new PreparedKamonTrace(context, operationName);
    }

    /**
     * Builds an empty {@link PreparedTrace}.
     *
     * @return the new prepared trace
     */
    public static PreparedTrace emptyPreparedTrace() {
        return EmptyPreparedTrace.getInstance();
    }

    /**
     * Builds an empty {@link StartedTrace}.
     *
     * @return the new prepared trace
     */
    public static StartedTrace emptyStartedTrace() {
        return EmptyStartedTrace.getInstance();
    }

}
