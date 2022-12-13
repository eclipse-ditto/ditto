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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.internal.utils.metrics.instruments.tag.KamonTagSetConverter;

import com.typesafe.config.Config;

import kamon.module.SpanReporter;
import kamon.trace.Identifier;
import kamon.trace.Span;
import scala.collection.immutable.Seq;
import scala.jdk.javaapi.CollectionConverters;

/**
 * It is impossible to get certain information of a running {@code Span} directly.
 * Thus, it is necessary to make a detour via reporting the span first.
 * For testing purposes a {@link Span.Finished} is much more valuable because it provides some interesting information.
 * This span reporter allows to asynchronously obtain a finished span for a particular {@link SpanId}.
 * <p>
 * Some helpers might be required to make testing with AssertJ more enjoyable:
 * {@link KamonTagSetConverter#getDittoTagSet(kamon.tag.TagSet)} and
 * {@link CollectionConverters#asJava(scala.collection.Seq)}.
 */
@NotThreadSafe
public final class TestSpanReporter implements SpanReporter {

    private final Map<SpanId, CompletableFuture<Span.Finished>> finishedSpanFutures;

    private TestSpanReporter() {
        super();
        finishedSpanFutures = new HashMap<>();
    }

    public static TestSpanReporter newInstance() {
        return new TestSpanReporter();
    }

    public CompletionStage<Span.Finished> getFinishedSpanForSpanWithId(final SpanId spanId) {
        final var result = new CompletableFuture<Span.Finished>();
        finishedSpanFutures.put(spanId, result);
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
            completeFinishedSpanFutureForSpan(getAsSpanId(span.id()), span);
            return span;
        });
    }

    private static SpanId getAsSpanId(final Identifier identifier) {
        return SpanId.of(identifier.string());
    }

    private void completeFinishedSpanFutureForSpan(final SpanId spanId, final Span.Finished finishedSpan) {
        @Nullable final var finishedSpanFuture = finishedSpanFutures.remove(spanId);
        if (null != finishedSpanFuture) {
            finishedSpanFuture.complete(finishedSpan);
        }
    }

}
