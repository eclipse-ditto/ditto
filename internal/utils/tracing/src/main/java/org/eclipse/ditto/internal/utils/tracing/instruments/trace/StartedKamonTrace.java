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

import org.eclipse.ditto.internal.utils.tracing.DittoTracing;

import kamon.context.Context;
import kamon.trace.Span;

/**
 * Kamon based implementation of {@code StartedTrace}. Basically wraps a Kamon {@link kamon.trace.Span}.
 */
class StartedKamonTrace implements StartedTrace {

    private final Span span;

    StartedKamonTrace(final Span span) {this.span = span;}

    @Override
    public StartedTrace tag(final String key, final String value) {
        span.tag(key, value);
        return this;
    }

    @Override
    public StartedTrace tags(final Map<String, String> tags) {
        tags.forEach(this::tag);
        return this;
    }

    @Override
    public void finish() {
        span.finish();
    }

    @Override
    public void finishAfter(final Duration duration) {
        span.finishAfter(duration);
    }

    @Override
    public StartedTrace fail(final String errorMessage) {
        span.fail(errorMessage);
        return this;
    }

    @Override
    public StartedTrace fail(final Throwable throwable) {
        span.fail(throwable);
        return this;
    }

    @Override
    public StartedTrace fail(final String errorMessage, final Throwable throwable) {
        span.fail(errorMessage, throwable);
        return this;
    }

    @Override
    public StartedTrace mark(final String key) {
        span.mark(key);
        return this;
    }

    @Override
    public StartedTrace mark(final String key, final Instant at) {
        span.mark(key, at);
        return this;
    }

    @Override
    public Map<String, String> propagateContext(final Map<String, String> map) {
        return DittoTracing.propagateContext(getContext(), map);
    }

    @Override
    public Context getContext() {
        return Context.of(Span.Key(), span);
    }
}
