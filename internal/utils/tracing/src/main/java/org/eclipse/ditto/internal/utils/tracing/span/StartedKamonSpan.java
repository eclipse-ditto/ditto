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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersBuilder;
import org.eclipse.ditto.internal.utils.metrics.instruments.tag.Tag;
import org.eclipse.ditto.internal.utils.metrics.instruments.tag.TagSet;

import kamon.context.Context;
import kamon.trace.Span;

/**
 * Kamon based implementation of {@code StartedSpan}. Basically wraps a Kamon {@link kamon.trace.Span}.
 */
@ThreadSafe // Thread safety results from `Span` being thread-safe.
final class StartedKamonSpan implements StartedSpan {

    private final Span span;
    private final KamonHttpContextPropagation httpContextPropagation;

    private StartedKamonSpan(final Span span, final KamonHttpContextPropagation httpContextPropagation) {
        this.span = span;
        this.httpContextPropagation = httpContextPropagation;
    }

    static StartedKamonSpan newInstance(final Span span, final KamonHttpContextPropagation httpContextPropagation) {
        return new StartedKamonSpan(
                checkNotNull(span, "span"),
                checkNotNull(httpContextPropagation, "httpContextPropagation")
        );
    }

    @Override
    public StartedSpan tag(final Tag tag) {
        checkNotNull(tag, "tag");
        span.tag(tag.getKey(), tag.getValue());
        return this;
    }

    @Override
    public StartedSpan tags(final TagSet tags) {
        checkNotNull(tags, "tags");
        tags.forEach(tag -> span.tag(tag.getKey(), tag.getValue()));
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
    public StartedSpan tagAsFailed(final String errorMessage) {
        span.fail(checkNotNull(errorMessage, "errorMessage"));
        return this;
    }

    @Override
    public StartedSpan tagAsFailed(final Throwable throwable) {
        span.fail(checkNotNull(throwable, "throwable"));
        return this;
    }

    @Override
    public StartedSpan tagAsFailed(final String errorMessage, final Throwable throwable) {
        span.fail(checkNotNull(errorMessage, "errorMessage"), checkNotNull(throwable, "throwable"));
        return this;
    }

    @Override
    public StartedSpan mark(final String key) {
        span.mark(checkNotNull(key, "key"));
        return this;
    }

    @Override
    public StartedSpan mark(final String key, final Instant at) {
        span.mark(checkNotNull(key, "key"), checkNotNull(at, "at"));
        return this;
    }

    @Override
    public SpanId getSpanId() {
        return SpanId.of(span.id().string());
    }

    @Override
    public SpanOperationName getOperationName() {
        return SpanOperationName.of(span.operationName());
    }

    @Override
    public Map<String, String> propagateContext(final Map<String, String> headers) {
        return httpContextPropagation.propagateContextToHeaders(wrapSpanInContext(
                headers.get(DittoHeaderDefinition.W3C_TRACESTATE.getKey())
        ), headers);
    }

    @Override
    public DittoHeaders propagateContext(final DittoHeaders dittoHeaders) {
        checkNotNull(dittoHeaders, "dittoHeaders");
        // Propagate onto a fresh, small map (delta) instead of copying all headers: the writer only ever emits the
        // W3C trace-context headers. The read side is identical to propagateContext(Map) - the same incoming
        // tracestate is read into the same context and this span's traceparent is written identically.
        final Map<String, String> propagatedContext = httpContextPropagation.propagateContextToNewMap(wrapSpanInContext(
                dittoHeaders.get(DittoHeaderDefinition.W3C_TRACESTATE.getKey())
        ));
        if (propagatedContext.isEmpty()) {
            return dittoHeaders;
        }
        // Apply only the propagated trace-context entries onto the already-validated, trusted headers. The changed
        // entries are the W3C trace-context headers, which carry a no-op value validator, so no header is re-parsed.
        final DittoHeadersBuilder<?, ?> builder = dittoHeaders.toBuilder();
        propagatedContext.forEach(builder::putHeader);
        return builder.build();
    }

    private Context wrapSpanInContext(@Nullable final String traceStateHeader) {
        if (traceStateHeader != null) {
            return Context.of(Span.Key(), span, Context.key("tracestate", ""), traceStateHeader);
        } else {
            return Context.of(Span.Key(), span);
        }
    }

    @Override
    public PreparedSpan spawnChild(final SpanOperationName operationName) {
        return TracingSpans.newPreparedKamonSpan(propagateContext(Map.of()), operationName, httpContextPropagation);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" +
                "span=" + span +
                ", httpContextPropagation=" + httpContextPropagation +
                "]";
    }
}
