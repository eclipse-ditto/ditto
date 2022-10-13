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
import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.internal.utils.tracing.TraceOperationName;

/**
 * An empty noop implementation of {@code StartedStrace} interface.
 */
@Immutable
final class EmptyStartedTrace implements StartedTrace {

    private final TraceOperationName operationName;

    private EmptyStartedTrace(final TraceOperationName operationName) {
        this.operationName = operationName;
    }

    static StartedTrace newInstance(final TraceOperationName operationName) {
        return new EmptyStartedTrace(ConditionChecker.checkNotNull(operationName, "operationName"));
    }

    @Override
    public StartedTrace tag(final String key, final String value) {
        return this;
    }

    @Override
    public StartedTrace tags(final Map<String, String> tags) {
        return this;
    }

    @Override
    public void finish() {
        // Nothing to finish in an empty started trace.
    }

    @Override
    public void finishAfter(final Duration duration) {
        // Nothing to finish in an empty started trace.
    }

    @Override
    public StartedTrace fail(final String errorMessage) {
        return this;
    }

    @Override
    public StartedTrace fail(final String errorMessage, final Throwable throwable) {
        return this;
    }

    @Override
    public StartedTrace mark(final String key) {
        return this;
    }

    @Override
    public StartedTrace mark(final String key, final Instant at) {
        return this;
    }

    @Override
    public SpanId getSpanId() {
        return SpanId.empty();
    }

    @Override
    public TraceOperationName getOperationName() {
        return operationName;
    }

    @Override
    public StartedTrace fail(final Throwable throwable) {
        return this;
    }

    @Override
    public Map<String, String> propagateContext(final Map<String, String> map) {
        return map;
    }

    @Override
    public PreparedTrace spawnChild(final TraceOperationName traceOperationName) {
        return Traces.emptyPreparedTrace(traceOperationName);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        final boolean result;
        if (this == o) {
            result = true;
        } else if (o instanceof EmptyStartedTrace that) {
            result = operationName.equals(that.operationName);
        } else {
            result = false;
        }
        return result;
    }

    @Override
    public int hashCode() {
        return Objects.hash(operationName);
    }

}
