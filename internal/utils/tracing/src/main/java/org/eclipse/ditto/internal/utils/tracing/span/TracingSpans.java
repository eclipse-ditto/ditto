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

import java.util.Map;

import org.eclipse.ditto.base.model.common.ConditionChecker;

/**
 * Factory methods for creating tracing spans.
 */
public final class TracingSpans {

    private TracingSpans() {
        throw new AssertionError();
    }

    /**
     * Returns a new instance of a Kamon {@code PreparedSpan} for the specified arguments.
     *
     * @param headers the headers from which to derive the span context.
     * @param operationName name of the operation to be traced.
     * @param kamonHttpContextPropagation derives and propagates the span context from and to headers.
     * @return the new instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static PreparedSpan newPreparedKamonSpan(
            final Map<String, String> headers,
            final SpanOperationName operationName,
            final KamonHttpContextPropagation kamonHttpContextPropagation
    ) {
        return PreparedKamonSpan.newInstance(headers, operationName, kamonHttpContextPropagation);
    }

    /**
     * Returns an empty {@code PreparedSpan}.
     *
     * @param operationName name of the operation the returned span is about.
     * @return the new prepared span.
     * @throws NullPointerException if {@code operationName} is {@code null}.
     */
    public static PreparedSpan emptyPreparedSpan(final SpanOperationName operationName) {
        return EmptyPreparedSpan.newInstance(ConditionChecker.checkNotNull(operationName, "operationName"));
    }

    /**
     * Returns an empty {@code StartedSpan} with the specified SpanOperationName argument.
     *
     * @param operationName name of the operation the returned span is about.
     * @return the new started span.
     * @throws NullPointerException if {@code operationName} is {@code null}.
     */
    public static StartedSpan emptyStartedSpan(final SpanOperationName operationName) {
        return EmptyStartedSpan.newInstance(operationName);
    }

}
