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

import java.time.Instant;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.tracing.DittoTracing;
import org.eclipse.ditto.internal.utils.tracing.TracingTags;

import kamon.Kamon;
import kamon.context.Context;
import kamon.tag.Lookups;
import kamon.tag.Tag;
import kamon.trace.Span;
import kamon.trace.SpanBuilder;
import scala.jdk.javaapi.CollectionConverters;

/**
 * Kamon based implementation of {@code PreparedTrace}.
 */
@NotThreadSafe
final class PreparedKamonTrace implements PreparedTrace {

    private final SpanBuilder spanBuilder;

    /**
     * Constructs a {@code PreparedKamonTrace} object.
     *
     * @throws NullPointerException if any argument is {@code null}.
     */
    PreparedKamonTrace(final Context context, final CharSequence operationName) {
        ConditionChecker.checkNotNull(context, "context");
        ConditionChecker.checkNotNull(operationName, "operationName");

        spanBuilder = Kamon.spanBuilder(operationName.toString()).asChildOf(context.get(Span.Key()));
    }

    @Override
    public PreparedTrace tag(final String key, final String value) {
        spanBuilder.tag(key, value);
        return this;
    }

    @Override
    public PreparedTrace tags(final Map<String, String> tags) {
        tags.forEach(this::tag);
        return this;
    }

    @Nullable
    @Override
    public String getTag(final String key) {
        ConditionChecker.checkNotNull(key, "key");
        final var tags = spanBuilder.tags();
        return tags.get(Lookups.plain(key));
    }

    @Override
    public Map<String, String> getTags() {
        final var tagSet = spanBuilder.tags();
        return CollectionConverters.asJava(tagSet.all())
                .stream()
                .collect(Collectors.toMap(Tag::key, PreparedKamonTrace::getTagValueAsString));
    }

    private static String getTagValueAsString(final Tag tag) {
        return String.valueOf(Tag.unwrapValue(tag));
    }

    @Override
    public StartedTrace start() {
        return new StartedKamonTrace(spanBuilder.start());
    }

    @Override
    public StartedTrace startAt(final Instant startInstant) {
        return new StartedKamonTrace(spanBuilder.start(startInstant));
    }

    @Override
    public <T> T run(final DittoHeaders dittoHeaders, final Function<DittoHeaders, T> function) {
        ConditionChecker.checkNotNull(dittoHeaders, "dittoHeaders");
        final var span = spanBuilder.start();
        return Kamon.runWithSpan(
                span,
                () -> {
                    tagSpanWithCorrelationId(span, dittoHeaders);
                    return function.apply(propagateTraceContextToDittoHeaders(dittoHeaders));
                }
        );
    }

    private static void tagSpanWithCorrelationId(final Span currentSpan, final DittoHeaders dittoHeaders) {
        dittoHeaders.getCorrelationId()
                .ifPresent(correlationId -> currentSpan.tag(TracingTags.CORRELATION_ID, correlationId));
    }

    private static DittoHeaders propagateTraceContextToDittoHeaders(final DittoHeaders dittoHeaders) {
        return DittoTracing.propagateContext(Kamon.currentContext(), dittoHeaders);
    }

    @Override
    public void run(final DittoHeaders dittoHeaders, final Consumer<DittoHeaders> consumer) {
        run(
                dittoHeaders,
                sameDittoHeaders -> {
                    consumer.accept(sameDittoHeaders);
                    return null;
                }
        );
    }

}
