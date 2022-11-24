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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.eclipse.ditto.internal.utils.tracing.span.SpanOperationName.of;

import java.text.MessageFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.NoSuchElementException;

import org.assertj.core.data.TemporalUnitWithinOffset;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.metrics.instruments.tag.KamonTagSetConverter;
import org.eclipse.ditto.internal.utils.metrics.instruments.tag.Tag;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import kamon.Kamon;
import kamon.trace.Identifier;
import kamon.trace.Span;
import scala.jdk.javaapi.CollectionConverters;

/**
 * Unit test for {@link StartedKamonSpan}.
 */
public final class StartedKamonSpanTest {

    @ClassRule
    public static final KamonTracingInitResource KAMON_TRACING_INIT_RESOURCE = KamonTracingInitResource.newInstance(
            KamonTracingInitResource.KamonTracingConfig.defaultValues()
                    .withSamplerAlways()
                    .withTickInterval(Duration.ofMillis(100L))
    );

    private static Identifier traceId;

    @Rule
    public final TestName testName = new TestName();

    @Rule
    public final KamonTestSpanReporterResource testSpanReporterResource = KamonTestSpanReporterResource.newInstance();

    private StartedSpan underTest;

    @BeforeClass
    public static void beforeClass() {
        traceId = Kamon.identifierScheme().traceIdFactory().generate();
    }

    @Before
    public void setup() {
        underTest = StartedKamonSpan.newInstance(
                Kamon.spanBuilder(testName.getMethodName())
                        .asChildOf(Kamon.spanBuilder("/").traceId(traceId).start())
                        .start(),
                KamonHttpContextPropagation.getInstanceForDefaultHttpChannel()
        );
    }

    @Test
    public void taggingWorks() {
        assertThatNoException().isThrownBy(() -> {
            underTest.tag("stringTag", "2");
            underTest.tag("booleanTag", true);
        });
    }

    @Test
    public void spanTags() {
        assertThatNoException().isThrownBy(() -> {
            underTest.correlationId("12345");
            underTest.connectionId("connection-1");
            underTest.entityId("test");
        });
    }

    @Test
    public void nullSpanTagsAreIgnored() {
        assertThatNoException().isThrownBy(() -> {
            underTest.correlationId(null);
            underTest.connectionId(null);
            underTest.entityId(null);
        });
    }

    @Test
    public void selfReturnsSelf() {
        assertThat(underTest.self()).isSameAs(underTest);
    }

    @Test
    public void markWithNullKeyThrowsNullPointerException() {
        assertThatNullPointerException()
                .isThrownBy(() -> underTest.mark(null))
                .withMessage("The key must not be null!")
                .withNoCause();
    }

    @Test
    public void markWithKeyAsOnlyArgumentCreatesMarkWithSpecifiedKeyCloseToCurrentInstant() {
        final var key = "successful";
        final var testSpanReporter = registerKamonTestSpanReporter();
        final var finishedSpanFuture = testSpanReporter.getFinishedSpanForSpanWithId(underTest.getSpanId());
        final var nowInstant = Instant.now();

        underTest.mark(key);
        underTest.finish();

        assertThat(finishedSpanFuture)
                .succeedsWithin(Duration.ofSeconds(1L))
                .satisfies(finishedSpan -> assertThat(CollectionConverters.asJava(finishedSpan.marks()))
                        .hasSize(1)
                        .first()
                        .satisfies(mark -> {
                            assertThat(mark.key()).isEqualTo(key);
                            assertThat(mark.instant())
                                    .isCloseTo(nowInstant, new TemporalUnitWithinOffset(500L, ChronoUnit.MILLIS));
                        }));
    }

    private TestSpanReporter registerKamonTestSpanReporter() {
        return testSpanReporterResource.registerTestSpanReporter(testName.getMethodName());
    }

    @Test
    public void markWithNullKeyButWithInstantThrowsNullPointerException() {
        assertThatNullPointerException()
                .isThrownBy(() -> underTest.mark(null, Instant.now()))
                .withMessage("The key must not be null!")
                .withNoCause();
    }

    @Test
    public void markWithNullInstantThrowsNullPointerException() {
        assertThatNullPointerException()
                .isThrownBy(() -> underTest.mark("myKey", null))
                .withMessage("The at must not be null!")
                .withNoCause();
    }

    @Test
    public void markWithInstantProducesExpectedMark() {
        final var key = "successful";
        final var testSpanReporter = registerKamonTestSpanReporter();
        final var finishedSpanFuture = testSpanReporter.getFinishedSpanForSpanWithId(underTest.getSpanId());
        final var mark = new Span.Mark(Instant.now(), key);

        underTest.mark(mark.key(), mark.instant());
        underTest.finish();

        assertThat(finishedSpanFuture)
                .succeedsWithin(Duration.ofSeconds(1L))
                .satisfies(finishedSpan -> {
                    assertThat(CollectionConverters.asJava(finishedSpan.marks())).containsOnly(mark);
                    assertThat(finishedSpan.hasError()).isFalse();
                    assertThat(KamonTagSetConverter.getDittoTagSet(finishedSpan.tags())).isEmpty();
                });
    }

    @Test
    public void tagAsFailedWithNullErrorMessageThrowsNullPointerException() {
        assertThatNullPointerException()
                .isThrownBy(() -> underTest.tagAsFailed((String) null))
                .withMessage("The errorMessage must not be null!")
                .withNoCause();
    }

    @Test
    public void tagAsFailedCreatesTagWithSpecifiedErrorMessage() {
        final var errorMessage = "A foo is not allowed to bar.";
        final var testSpanReporter = registerKamonTestSpanReporter();
        final var finishedSpanFuture = testSpanReporter.getFinishedSpanForSpanWithId(underTest.getSpanId());

        underTest.tagAsFailed(errorMessage);
        underTest.finish();

        assertThat(finishedSpanFuture)
                .succeedsWithin(Duration.ofSeconds(1L))
                .satisfies(finishedSpan -> {
                    assertThat(CollectionConverters.asJava(finishedSpan.marks())).isEmpty();
                    assertThat(finishedSpan.hasError()).isTrue();
                    assertThat(KamonTagSetConverter.getDittoTagSet(finishedSpan.tags()))
                            .containsOnly(Tag.of("error.message", errorMessage));
                });
    }

    @Test
    public void tagAsFailedWithNullThrowableThrowsNullPointerException() {
        assertThatNullPointerException()
                .isThrownBy(() -> underTest.tagAsFailed((Throwable) null))
                .withMessage("The throwable must not be null!")
                .withNoCause();
    }

    @Test
    public void tagAsFailedWithThrowableCreatesExpectedTags() {
        final var throwable = new NoSuchElementException("Hypermatter");
        final var testSpanReporter = registerKamonTestSpanReporter();
        final var finishedSpanFuture = testSpanReporter.getFinishedSpanForSpanWithId(underTest.getSpanId());

        underTest.tagAsFailed(throwable);
        underTest.finish();

        assertThat(finishedSpanFuture)
                .succeedsWithin(Duration.ofSeconds(1L))
                .satisfies(finishedSpan -> {
                    assertThat(CollectionConverters.asJava(finishedSpan.marks())).isEmpty();
                    assertThat(finishedSpan.hasError()).isTrue();
                    assertThat(KamonTagSetConverter.getDittoTagSet(finishedSpan.tags()))
                            .hasSize(3)
                            .contains(
                                    Tag.of("error.type", throwable.getClass().getName()),
                                    Tag.of("error.message", throwable.getMessage())
                            )
                            .anyMatch(tag -> "error.stacktrace".equals(tag.getKey()));
                });
    }

    @Test
    public void tagAsFailedWithErrorMessageAndThrowableCreatesExpectedTags() {
        final var errorMessage = "Failed to fire tachyon pulses.";
        final var throwable = new NoSuchElementException("Tachyons");
        final var testSpanReporter = registerKamonTestSpanReporter();
        final var finishedSpanFuture = testSpanReporter.getFinishedSpanForSpanWithId(underTest.getSpanId());

        underTest.tagAsFailed(errorMessage, throwable);
        underTest.finish();

        assertThat(finishedSpanFuture)
                .succeedsWithin(Duration.ofSeconds(1L))
                .satisfies(finishedSpan -> {
                    assertThat(CollectionConverters.asJava(finishedSpan.marks())).isEmpty();
                    assertThat(finishedSpan.hasError()).isTrue();
                    assertThat(KamonTagSetConverter.getDittoTagSet(finishedSpan.tags()))
                            .hasSize(3)
                            .contains(
                                    Tag.of("error.type", throwable.getClass().getName()),
                                    Tag.of("error.message", errorMessage)
                            )
                            .anyMatch(tag -> "error.stacktrace".equals(tag.getKey()));
                });
    }

    @Test
    public void getSpanIdReturnsNonEmptySpanId() {
        assertThat((CharSequence) underTest.getSpanId()).isNotEmpty();
    }

    @Test
    public void getOperationNameReturnsExpected() {
        assertThat((CharSequence) underTest.getOperationName()).isEqualTo(of(testName.getMethodName()));
    }

    @Test
    // Note: for the receiver the trace parent is the current span.
    public void propagateContextToDittoHeadersPutsW3cTraceparent() {
        final var dittoHeaders = DittoHeaders.newBuilder().correlationId(testName.getMethodName()).build();

        final var dittoHeadersAfterPropagation = underTest.propagateContext(dittoHeaders);

        assertThat(dittoHeadersAfterPropagation)
                .containsAllEntriesOf(dittoHeaders)
                .containsEntry(DittoHeaderDefinition.W3C_TRACEPARENT.getKey(), getExpectedTraceparentValue());
    }

    private String getExpectedTraceparentValue() {
        return MessageFormat.format("00-{0}-{1}-01", traceId.string(), underTest.getSpanId());
    }

    @Test
    // Note: for the receiver the trace parent is the current span.
    public void propagateContextToEmptyMapPutsW3cTracingHeadersAndContextTags() {
        final var dittoHeadersAfterPropagation = underTest.propagateContext(Map.of());

        assertThat(dittoHeadersAfterPropagation)
                .containsOnly(
                        Map.entry(DittoHeaderDefinition.W3C_TRACEPARENT.getKey(), getExpectedTraceparentValue()),
                        Map.entry(DittoHeaderDefinition.W3C_TRACESTATE.getKey(), ""),
                        Map.entry("context-tags", "upstream.name=kamon-application;")
                );
    }

    @Test
    public void spawnChildWithNullOperationNameThrowsNullPointerException() {
        assertThatNullPointerException()
                .isThrownBy(() -> underTest.spawnChild(null))
                .withMessage("The operationName must not be null!")
                .withNoCause();
    }

    @Test
    public void spawnChildReturnsExpectedPreparedSpan() {
        final var childSpan = underTest.spawnChild(of("foo"));

        assertThat(childSpan).isNotNull();
    }

}
