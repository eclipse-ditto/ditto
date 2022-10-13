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

import java.util.Map;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.internal.utils.tracing.TraceOperationName;

/**
 * Factory methods for traces.
 */
public final class Traces {

    private Traces() {
        throw new AssertionError();
    }

    /**
     * Returns a new instance of {@code PreparedKamonTrace} for the specified arguments.
     *
     * @param headers the headers from which to derive the trace context.
     * @param traceOperationName name of the operation to be traced.
     * @param kamonHttpContextPropagation derives and propagates the trace context from and to headers.
     * @return the new instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static PreparedTrace newPreparedKamonTrace(
            final Map<String, String> headers,
            final TraceOperationName traceOperationName,
            final KamonHttpContextPropagation kamonHttpContextPropagation
    ) {
        return PreparedKamonTrace.newInstance(headers, traceOperationName, kamonHttpContextPropagation);
    }

    /**
     * Returns an empty {@code PreparedTrace}.
     *
     * @param operationName name of the operation the returned span is about.
     * @return the new prepared trace.
     * @throws NullPointerException if {@code operationName} is {@code null}.
     */
    public static PreparedTrace emptyPreparedTrace(final TraceOperationName operationName) {
        return EmptyPreparedTrace.newInstance(ConditionChecker.checkNotNull(operationName, "operationName"));
    }

    /**
     * Returns an empty {@code StartedTrace} with the specified TraceOperationName argument.
     *
     * @param operationName name of the operation the returned span is about.
     * @return the new started trace.
     * @throws NullPointerException if {@code operationName} is {@code null}.
     */
    public static StartedTrace emptyStartedTrace(final TraceOperationName operationName) {
        return EmptyStartedTrace.newInstance(operationName);
    }

}
