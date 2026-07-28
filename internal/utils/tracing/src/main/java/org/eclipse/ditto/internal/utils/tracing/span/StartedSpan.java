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

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersBuilder;
import org.eclipse.ditto.internal.utils.metrics.instruments.TaggableMetricsInstrument;

/**
 * A started tracing span.
 */
public interface StartedSpan extends TaggableMetricsInstrument<StartedSpan>, SpanTagging<StartedSpan> {

    @Override
    default StartedSpan self() {
        return this;
    }

    /**
     * Finishes this span. Any method called after the span is finished has no effect.
     */
    void finish();

    /**
     * Finishes this span with the given duration. Any method called after the span is finished has no effect.
     *
     * @param duration the duration after which to finish this span.
     */
    void finishAfter(Duration duration);

    /**
     * Adds a new mark with the provided key using the current instant.
     *
     * @param key the key of the created mark.
     * @return this span.
     * @throws NullPointerException if {@code key} is {@code null}.
     */
    StartedSpan mark(String key);

    /**
     * Adds a new mark with the provided key using the provided instant.
     *
     * @param key the key of the created mark.
     * @param at the provided instant.
     * @return this span.
     * @throws NullPointerException if any argument is {@code null}.
     */
    StartedSpan mark(String key, Instant at);

    /**
     * Sets this span to failed state and adds a tag with key {@code "error.message"} and the provided error message as
     * value.
     *
     * @param errorMessage message describing the error.
     * @return this span.
     * @throws NullPointerException if {@code errorMessage} is {@code null}.
     */
    StartedSpan tagAsFailed(String errorMessage);

    /**
     * Sets this span to failed state and optionally adds the provided error stack trace as a tag.
     * See the "kamon.trace.include-error-stacktrace" setting for more information.
     *
     * @param throwable the throwable.
     * @return this span.
     * @throws NullPointerException if {@code throwable} is {@code null}.
     */
    StartedSpan tagAsFailed(Throwable throwable);

    /**
     * Sets this span to failed state and adds the provided error message as a tag and optionally adds the provided
     * error stack trace as a tag.
     *
     * @param errorMessage message describing the error.
     * @param throwable the throwable.
     * @return this span.
     */
    StartedSpan tagAsFailed(String errorMessage, Throwable throwable);

    /**
     * Returns the identifier of this tracing span.
     *
     * @return the identifier.
     */
    SpanId getSpanId();

    /**
     * Returns the operation name of this tracing span.
     *
     * @return the operation name.
     */
    SpanOperationName getOperationName();

    /**
     * Propagates the current context of this span to the specified map.
     *
     * @param map the map to which the current span context is propagated.
     */
    Map<String, String> propagateContext(Map<String, String> map);

    /**
     * Propagates the current context of this span onto the given <em>already-validated</em> {@link DittoHeaders},
     * returning trusted {@code DittoHeaders} that skip header value re-validation.
     * <p>
     * This is equivalent in result to {@code DittoHeaders.of(propagateContext((Map<String, String>) dittoHeaders))}
     * but avoids rebuilding through the validating constructor, which would re-parse every JSON-typed header value on
     * each traced hop. It runs the exact same propagation on the actual headers (so the incoming {@code tracestate}
     * is read and this span's {@code traceparent} is written identically to {@link #propagateContext(Map)}), then
     * re-applies only the entries the propagation actually changed onto the trusted, memoizing headers. Those changed
     * entries are the W3C trace-context headers ({@code traceparent}/{@code tracestate}), which carry a no-op value
     * validator, so no header value is re-parsed.
     *
     * @param dittoHeaders the already-validated headers onto which the span context is propagated.
     * @return the trusted headers including the propagated span context.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    default DittoHeaders propagateContext(final DittoHeaders dittoHeaders) {
        final Map<String, String> propagatedHeaders = propagateContext((Map<String, String>) dittoHeaders);
        DittoHeadersBuilder<?, ?> builder = null;
        for (final Map.Entry<String, String> entry : propagatedHeaders.entrySet()) {
            if (!Objects.equals(entry.getValue(), dittoHeaders.get(entry.getKey()))) {
                if (null == builder) {
                    builder = dittoHeaders.toBuilder();
                }
                builder.putHeader(entry.getKey(), entry.getValue());
            }
        }
        return null == builder ? dittoHeaders : builder.build();
    }

    /**
     * Spawns a child span of this StartedSpan with the specified SpanOperationName argument.
     *
     * @param operationName the operation name of the returned child span.
     * @return the child span of this span.
     * @throws NullPointerException if {@code operationName} is {@code null}.
     */
    PreparedSpan spawnChild(SpanOperationName operationName);

}
