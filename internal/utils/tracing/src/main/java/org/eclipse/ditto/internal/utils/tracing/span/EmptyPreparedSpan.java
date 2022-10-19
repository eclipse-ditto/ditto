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

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.internal.utils.metrics.instruments.timer.StartInstant;
import org.eclipse.ditto.internal.utils.metrics.instruments.timer.StartedTimer;

/**
 * An empty noop implementation of {@code PreparedSpan} interface.
 */
@Immutable
final class EmptyPreparedSpan implements PreparedSpan {

    private final SpanOperationName operationName;

    private EmptyPreparedSpan(final SpanOperationName operationName) {
        this.operationName = operationName;
    }

    static EmptyPreparedSpan newInstance(final SpanOperationName operationName) {
        return new EmptyPreparedSpan(ConditionChecker.checkNotNull(operationName, "operationName"));
    }

    @Override
    public EmptyPreparedSpan tag(final String key, final String value) {
        return this;
    }

    @Override
    public EmptyPreparedSpan tags(final Map<String, String> tags) {
        return this;
    }

    @Override
    public Optional<String> getTag(final String key) {
        return Optional.empty();
    }

    @Override
    public Map<String, String> getTags() {
        return Collections.emptyMap();
    }

    @Override
    public StartedSpan start() {
        return TracingSpans.emptyStartedSpan(operationName);
    }

    @Override
    public StartedSpan startAt(final StartInstant startInstant) {
        return TracingSpans.emptyStartedSpan(operationName);
    }

    @Override
    public StartedSpan startBy(final StartedTimer startedTimer) {
        return TracingSpans.emptyStartedSpan(operationName);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final var that = (EmptyPreparedSpan) o;
        return Objects.equals(operationName, that.operationName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(operationName);
    }

}
