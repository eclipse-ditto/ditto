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
class PreparedKamonTrace implements PreparedTrace {

    private final SpanBuilder spanBuilder;

    PreparedKamonTrace(final Context context, final String operationName) {
        final Span parent = context.get(Span.Key());
        spanBuilder = Kamon.spanBuilder(operationName).asChildOf(parent);
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
        return spanBuilder.tags().get(Lookups.plain(key));
    }

    @Override
    public Map<String, String> getTags() {
        return CollectionConverters.asJava(spanBuilder.tags().all())
                .stream()
                .collect(Collectors.toMap(Tag::key, t -> Tag.unwrapValue(t).toString()));
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
        return Kamon.runWithSpan(spanBuilder.start(), () -> {
            final DittoHeaders dittoHeadersWithContext = DittoTracing.propagateContext(Kamon.currentContext(),
                    dittoHeaders);
            dittoHeaders.getCorrelationId().ifPresent(cid -> Kamon.currentSpan().tag(TracingTags.CONNECTION_ID, cid));
            return function.apply(dittoHeadersWithContext);
        });
    }

    @Override
    public void run(final DittoHeaders dittoHeaders, final Consumer<DittoHeaders> consumer) {
        Kamon.runWithSpan(spanBuilder.start(), () -> {
            final DittoHeaders dittoHeadersWithContext = DittoTracing.propagateContext(Kamon.currentContext(),
                    dittoHeaders);
            dittoHeaders.getCorrelationId().ifPresent(cid -> Kamon.currentSpan().tag(TracingTags.CONNECTION_ID, cid));
            consumer.accept(dittoHeadersWithContext);
            return null;
        });
    }
}
