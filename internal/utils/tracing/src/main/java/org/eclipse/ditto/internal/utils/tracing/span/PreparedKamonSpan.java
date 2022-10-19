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

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.internal.utils.metrics.instruments.timer.StartInstant;
import org.eclipse.ditto.internal.utils.metrics.instruments.timer.StartedTimer;

import kamon.Kamon;
import kamon.tag.Lookups;
import kamon.tag.Tag;
import kamon.trace.Span;
import kamon.trace.SpanBuilder;
import scala.jdk.javaapi.CollectionConverters;

/**
 * Kamon based implementation of {@code PreparedSpan}.
 */
@NotThreadSafe
final class PreparedKamonSpan implements PreparedSpan {

    private final SpanBuilder spanBuilder;
    private final KamonHttpContextPropagation httpContextPropagation;

    private PreparedKamonSpan(
            final SpanOperationName operationName,
            final Map<String, String> headers,
            final KamonHttpContextPropagation httpContextPropagation
    ) {
        final var context = httpContextPropagation.getContextFromHeaders(headers);
        spanBuilder = Kamon.spanBuilder(operationName.toString()).asChildOf(context.get(Span.Key()));
        this.httpContextPropagation = httpContextPropagation;
    }

    /**
     * Returns a new instance of {@code PreparedKamonSpan} for the specified arguments.
     *
     * @param headers the headers from which to derive the span context.
     * @param operationName name of the operation to be traced.
     * @param kamonHttpContextPropagation derives and propagates the span context from and to headers.
     * @return the new instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static PreparedKamonSpan newInstance(
            final Map<String, String> headers,
            final SpanOperationName operationName,
            final KamonHttpContextPropagation kamonHttpContextPropagation
    ) {
        final var result = new PreparedKamonSpan(
                checkNotNull(operationName, "operationName"),
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
    public PreparedKamonSpan tag(final String key, final String value) {
        spanBuilder.tag(checkNotNull(key, "key"), checkNotNull(value, "value"));
        return this;
    }

    @Override
    public PreparedKamonSpan tags(final Map<String, String> tags) {
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
                .collect(Collectors.toMap(Tag::key, PreparedKamonSpan::getTagValueAsString));
    }

    private static String getTagValueAsString(final Tag tag) {
        return String.valueOf(Tag.unwrapValue(tag));
    }

    @Override
    public StartedKamonSpan start() {
        return getStartedSpan(spanBuilder.start());
    }

    private StartedKamonSpan getStartedSpan(final Span span) {
        return StartedKamonSpan.newInstance(span, httpContextPropagation);
    }

    @Override
    public StartedKamonSpan startAt(final StartInstant startInstant) {
        checkNotNull(startInstant, "startInstant");
        return getStartedSpan(spanBuilder.start(startInstant.toInstant()));
    }

    @Override
    public StartedKamonSpan startBy(final StartedTimer startedTimer) {
        checkNotNull(startedTimer, "startedTimer");
        final var result = startAt(startedTimer.getStartInstant());
        startedTimer.onStop(stoppedTimer -> {
            result.tags(stoppedTimer.getTags());
            result.finishAfter(stoppedTimer.getDuration());
        });
        return result;
    }

}
