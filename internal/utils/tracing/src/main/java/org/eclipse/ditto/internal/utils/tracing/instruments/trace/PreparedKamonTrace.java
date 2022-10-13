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

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.internal.utils.metrics.instruments.timer.StartInstant;
import org.eclipse.ditto.internal.utils.metrics.instruments.timer.StartedTimer;
import org.eclipse.ditto.internal.utils.tracing.TraceOperationName;

import kamon.Kamon;
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
    private final KamonHttpContextPropagation httpContextPropagation;

    private PreparedKamonTrace(
            final TraceOperationName operationName,
            final Map<String, String> headers,
            final KamonHttpContextPropagation httpContextPropagation
    ) {
        final var context = httpContextPropagation.getContextFromHeaders(headers);
        spanBuilder = Kamon.spanBuilder(operationName.toString()).asChildOf(context.get(Span.Key()));
        this.httpContextPropagation = httpContextPropagation;
    }

    /**
     * Returns a new instance of {@code PreparedKamonTrace} for the specified arguments.
     *
     * @param headers the headers from which to derive the trace context.
     * @param traceOperationName name of the operation to be traced.
     * @param kamonHttpContextPropagation derives and propagates the trace context from and to headers.
     * @return the new instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static PreparedKamonTrace newInstance(
            final Map<String, String> headers,
            final TraceOperationName traceOperationName,
            final KamonHttpContextPropagation kamonHttpContextPropagation
    ) {
        final var result = new PreparedKamonTrace(
                checkNotNull(traceOperationName, "traceOperationName"),
                checkNotNull(headers, "headers"),
                checkNotNull(kamonHttpContextPropagation, "kamonHttpContextPropagation")
        );
        result.addWellKnownTracingTagsFromHeaders(headers);
        return result;
    }

    private void addWellKnownTracingTagsFromHeaders(final Map<String, String> headers) {
        correlationId(headers.get(DittoHeaderDefinition.CORRELATION_ID.getKey()));
        connectionId(headers.get(DittoHeaderDefinition.CONNECTION_ID.getKey()));
        entityId(headers.get(DittoHeaderDefinition.ENTITY_ID.getKey()));
    }

    @Override
    public PreparedKamonTrace tag(final String key, final String value) {
        spanBuilder.tag(checkNotNull(key, "key"), checkNotNull(value, "value"));
        return this;
    }

    @Override
    public PreparedKamonTrace tags(final Map<String, String> tags) {
        checkNotNull(tags, "tags");
        tags.forEach(this::tag);
        return this;
    }

    @Override
    public Optional<String> getTag(final String key) {
        checkNotNull(key, "key");
        final var tags = spanBuilder.tags();
        return Optional.ofNullable(tags.get(Lookups.plain(key)));
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
    public StartedKamonTrace start() {
        return getStartedTrace(spanBuilder.start());
    }

    private StartedKamonTrace getStartedTrace(final Span span) {
        return StartedKamonTrace.newInstance(span, httpContextPropagation);
    }

    @Override
    public StartedKamonTrace startAt(final StartInstant startInstant) {
        checkNotNull(startInstant, "traceStartInstant");
        return getStartedTrace(spanBuilder.start(startInstant.toInstant()));
    }

    @Override
    public StartedKamonTrace startBy(final StartedTimer startedTimer) {
        checkNotNull(startedTimer, "startedTimer");
        final var result = startAt(startedTimer.getStartInstant());
        startedTimer.onStop(stoppedTimer -> {
            result.tags(stoppedTimer.getTags());
            result.finishAfter(stoppedTimer.getDuration());
        });
        return result;
    }

}
