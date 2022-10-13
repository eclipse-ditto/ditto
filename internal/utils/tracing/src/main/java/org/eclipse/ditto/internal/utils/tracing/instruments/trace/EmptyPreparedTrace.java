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

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.internal.utils.metrics.instruments.timer.StartInstant;
import org.eclipse.ditto.internal.utils.metrics.instruments.timer.StartedTimer;
import org.eclipse.ditto.internal.utils.tracing.TraceOperationName;

/**
 * An empty noop implementation of {@code PreparedTrace} interface.
 */
@Immutable
final class EmptyPreparedTrace implements PreparedTrace {

    private final TraceOperationName operationName;

    private EmptyPreparedTrace(final TraceOperationName operationName) {
        this.operationName = operationName;
    }

    static EmptyPreparedTrace newInstance(final TraceOperationName operationName) {
        return new EmptyPreparedTrace(ConditionChecker.checkNotNull(operationName, "operationName"));
    }

    @Override
    public EmptyPreparedTrace tag(final String key, final String value) {
        return this;
    }

    @Override
    public EmptyPreparedTrace tags(final Map<String, String> tags) {
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
    public StartedTrace start() {
        return Traces.emptyStartedTrace(operationName);
    }

    @Override
    public StartedTrace startAt(final StartInstant startInstant) {
        return Traces.emptyStartedTrace(operationName);
    }

    @Override
    public StartedTrace startBy(final StartedTimer startedTimer) {
        return Traces.emptyStartedTrace(operationName);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final var that = (EmptyPreparedTrace) o;
        return Objects.equals(operationName, that.operationName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(operationName);
    }

}
