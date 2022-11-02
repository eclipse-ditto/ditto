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

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.internal.utils.metrics.instruments.tag.Tag;
import org.eclipse.ditto.internal.utils.metrics.instruments.tag.TagSet;

/**
 * An empty noop implementation of {@code StartedSpan} interface.
 */
@Immutable
final class EmptyStartedSpan implements StartedSpan {

    private final SpanOperationName operationName;

    private EmptyStartedSpan(final SpanOperationName operationName) {
        this.operationName = operationName;
    }

    static StartedSpan newInstance(final SpanOperationName operationName) {
        return new EmptyStartedSpan(ConditionChecker.checkNotNull(operationName, "operationName"));
    }

    @Override
    public EmptyStartedSpan tag(final Tag tag) {
        return this;
    }

    @Override
    public EmptyStartedSpan tags(final TagSet tags) {
        return this;
    }

    @Override
    public void finish() {
        // Nothing to finish in an empty started span.
    }

    @Override
    public void finishAfter(final Duration duration) {
        // Nothing to finish in an empty started span.
    }

    @Override
    public StartedSpan tagAsFailed(final String errorMessage) {
        return this;
    }

    @Override
    public StartedSpan tagAsFailed(final Throwable throwable) {
        return this;
    }

    @Override
    public StartedSpan tagAsFailed(final String errorMessage, final Throwable throwable) {
        return this;
    }

    @Override
    public StartedSpan mark(final String key) {
        return this;
    }

    @Override
    public StartedSpan mark(final String key, final Instant at) {
        return this;
    }

    @Override
    public SpanId getSpanId() {
        return SpanId.empty();
    }

    @Override
    public SpanOperationName getOperationName() {
        return operationName;
    }

    @Override
    public Map<String, String> propagateContext(final Map<String, String> map) {
        return map;
    }

    @Override
    public PreparedSpan spawnChild(final SpanOperationName operationName) {
        return TracingSpans.emptyPreparedSpan(operationName);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        final boolean result;
        if (this == o) {
            result = true;
        } else if (o instanceof EmptyStartedSpan that) {
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
