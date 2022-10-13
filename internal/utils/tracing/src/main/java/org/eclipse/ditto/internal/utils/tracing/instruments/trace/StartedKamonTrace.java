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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import javax.annotation.concurrent.ThreadSafe;

import org.eclipse.ditto.internal.utils.tracing.TraceOperationName;

import kamon.context.Context;
import kamon.trace.Span;

/**
 * Kamon based implementation of {@code StartedTrace}. Basically wraps a Kamon {@link kamon.trace.Span}.
 */
@ThreadSafe // Thread safety results from `Span` being thread-safe.
final class StartedKamonTrace implements StartedTrace {

    private final Span span;
    private final KamonHttpContextPropagation httpContextPropagation;

    private StartedKamonTrace(final Span span, final KamonHttpContextPropagation httpContextPropagation) {
        this.span = span;
        this.httpContextPropagation = httpContextPropagation;
    }

    static StartedKamonTrace newInstance(final Span span, final KamonHttpContextPropagation httpContextPropagation) {
        return new StartedKamonTrace(
                checkNotNull(span, "span"),
                checkNotNull(httpContextPropagation, "httpContextPropagation")
        );
    }

    @Override
    public StartedTrace tag(final String key, final String value) {
        span.tag(checkNotNull(key, "key"), checkNotNull(value, "value"));
        return this;
    }

    @Override
    public StartedTrace tags(final Map<String, String> tags) {
        checkNotNull(tags, "tags");
        tags.forEach(this::tag);
        return this;
    }

    @Override
    public void finish() {
        span.finish();
    }

    @Override
    public void finishAfter(final Duration duration) {
        span.finishAfter(checkNotNull(duration, "duration"));
    }

    @Override
    public StartedTrace fail(final String errorMessage) {
        span.fail(checkNotNull(errorMessage, "errorMessage"));
        return this;
    }

    @Override
    public StartedTrace fail(final Throwable throwable) {
        span.fail(checkNotNull(throwable, "throwable"));
        return this;
    }

    @Override
    public StartedTrace fail(final String errorMessage, final Throwable throwable) {
        span.fail(errorMessage, throwable);
        return this;
    }

    @Override
    public StartedTrace mark(final String key) {
        span.mark(checkNotNull(key, "key"));
        return this;
    }

    @Override
    public StartedTrace mark(final String key, final Instant at) {
        span.mark(checkNotNull(key, "key"), checkNotNull(at, "at"));
        return this;
    }

    @Override
    public SpanId getSpanId() {
        return SpanId.of(span.id().string());
    }

    @Override
    public TraceOperationName getOperationName() {
        return TraceOperationName.of(span.operationName());
    }

    @Override
    public Map<String, String> propagateContext(final Map<String, String> headers) {
        return httpContextPropagation.propagateContextToHeaders(wrapSpanInContext(), headers);
    }

    private Context wrapSpanInContext() {
        return Context.of(Span.Key(), span);
    }

    @Override
    public PreparedTrace spawnChild(final TraceOperationName traceOperationName) {
        return Traces.newPreparedKamonTrace(propagateContext(Map.of()), traceOperationName, httpContextPropagation);
    }

}
