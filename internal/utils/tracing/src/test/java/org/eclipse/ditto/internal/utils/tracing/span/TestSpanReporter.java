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

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.internal.utils.metrics.instruments.tag.KamonTagSetConverter;
import org.eclipse.ditto.internal.utils.metrics.instruments.tag.TagSet;

import com.typesafe.config.Config;

import kamon.module.SpanReporter;
import kamon.trace.Identifier;
import kamon.trace.Span;
import scala.collection.immutable.Seq;

/**
 * It is impossible to get the tags of a running {@code Span} directly.
 * Thus, it is necessary to make a detour via reporting the span first.
 * On a finished span it is possible to obtain the tags.
 */
@NotThreadSafe
public final class TestSpanReporter implements SpanReporter {

    private final Map<SpanId, CompletableFuture<TagSet>> tagsOfSpanFutures;
    private final Map<SpanId, CompletableFuture<Instant>> startInstantOfSpanFutures;

    private TestSpanReporter() {
        super();
        tagsOfSpanFutures = new HashMap<>();
        startInstantOfSpanFutures = new HashMap<>();
    }

    public static TestSpanReporter newInstance() {
        return new TestSpanReporter();
    }

    public CompletionStage<TagSet> getTagsForSpanWithId(final SpanId spanId) {
        final var result = new CompletableFuture<TagSet>();
        tagsOfSpanFutures.put(spanId, result);
        return result;
    }

    public CompletionStage<Instant> getStartInstantForSpanWithId(final SpanId spanId) {
        final var result = new CompletableFuture<Instant>();
        startInstantOfSpanFutures.put(spanId, result);
        return result;
    }

    @Override
    public void stop() {
        // Nothing to do here.
    }

    @Override
    public void reconfigure(final Config newConfig) {
        // Nothing to do here.
    }

    @Override
    public void reportSpans(final Seq<Span.Finished> spans) {
        spans.foreach(span -> {
            final var spanId = getAsSpanId(span.id());
            completeTagsOfSpan(spanId, span.tags());
            completeStartInstantOfSpan(spanId, span.from());
            return span;
        });
    }

    private static SpanId getAsSpanId(final Identifier identifier) {
        return SpanId.of(identifier.string());
    }

    private void completeTagsOfSpan(final SpanId spanId, final kamon.tag.TagSet kamonTagSet) {
        @Nullable final var tagsFuture = tagsOfSpanFutures.remove(spanId);
        if (null != tagsFuture) {
            tagsFuture.complete(KamonTagSetConverter.getDittoTagSet(kamonTagSet));
        }
    }

    private void completeStartInstantOfSpan(final SpanId spanId, final Instant startInstant) {
        @Nullable final var startInstantFuture = startInstantOfSpanFutures.remove(spanId);
        if (null != startInstantFuture) {
            startInstantFuture.complete(startInstant);
        }
    }

}
