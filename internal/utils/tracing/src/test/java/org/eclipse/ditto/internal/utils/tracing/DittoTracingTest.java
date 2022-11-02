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
package org.eclipse.ditto.internal.utils.tracing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.assertj.core.api.AutoCloseableSoftAssertions;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.eclipse.ditto.internal.utils.metrics.instruments.tag.KamonTagSetConverter;
import org.eclipse.ditto.internal.utils.metrics.instruments.tag.Tag;
import org.eclipse.ditto.internal.utils.metrics.instruments.tag.TagSet;
import org.eclipse.ditto.internal.utils.metrics.instruments.timer.Timers;
import org.eclipse.ditto.internal.utils.tracing.config.DefaultTracingConfig;
import org.eclipse.ditto.internal.utils.tracing.config.TracingConfig;
import org.eclipse.ditto.internal.utils.tracing.filter.AcceptAllTracingFilter;
import org.eclipse.ditto.internal.utils.tracing.span.KamonTestSpanReporterResource;
import org.eclipse.ditto.internal.utils.tracing.span.KamonTracingInitResource;
import org.eclipse.ditto.internal.utils.tracing.span.SpanOperationName;
import org.eclipse.ditto.internal.utils.tracing.span.SpanTagKey;
import org.eclipse.ditto.internal.utils.tracing.span.TracingSpans;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.mockito.Mockito;

import com.typesafe.config.ConfigFactory;

/**
 * Unit test for {@link DittoTracing}.
 */
public final class DittoTracingTest {

    @ClassRule
    public static final KamonTracingInitResource KAMON_TRACING_INIT_RESOURCE = KamonTracingInitResource.newInstance(
            KamonTracingInitResource.KamonTracingConfig.defaultValues()
                    .withSamplerAlways()
                    .withTickInterval(Duration.ofMillis(100L))
    );

    @Rule
    public final TestName testName = new TestName();

    @Rule
    public final KamonTestSpanReporterResource testSpanReporterResource = KamonTestSpanReporterResource.newInstance();

    private TracingConfig tracingConfigMock;

    @Before
    public void before() {
        tracingConfigMock = Mockito.mock(TracingConfig.class);
        Mockito.when(tracingConfigMock.isTracingEnabled()).thenReturn(true);
        Mockito.when(tracingConfigMock.getPropagationChannel()).thenReturn("default");
        Mockito.when(tracingConfigMock.getTracingFilter()).thenReturn(AcceptAllTracingFilter.getInstance());
    }

    @After
    public void after() {
        DittoTracing.reset();
    }

    @Test
    public void initWithUndefinedPropagationChannelNameInConfigThrowsDittoConfigError() {
        final var invalidChannelName = "zoeglfrex";
        Mockito.when(tracingConfigMock.getPropagationChannel()).thenReturn(invalidChannelName);

        assertThatExceptionOfType(DittoConfigError.class)
                .isThrownBy(() -> DittoTracing.init(tracingConfigMock))
                .withMessage("HTTP propagation for channel name <%s> is undefined.", invalidChannelName)
                .withCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void callInitOnAlreadyInitializedDisabledDittoTracingThrowsIllegalStateException() {
        Mockito.when(tracingConfigMock.isTracingEnabled()).thenReturn(false);
        DittoTracing.init(tracingConfigMock);

        assertThatIllegalStateException()
                .isThrownBy(() -> DittoTracing.init(tracingConfigMock))
                .withMessage(
                        "%s was already initialized. Please ensure that initialization is only performed once.",
                        DittoTracing.class.getSimpleName()
                )
                .withNoCause();
    }

    @Test
    public void callInitOnAlreadyInitializedEnabledDittoTracingThrowsIllegalStateException() {
        Mockito.when(tracingConfigMock.isTracingEnabled()).thenReturn(true);
        DittoTracing.init(tracingConfigMock);

        assertThatIllegalStateException()
                .isThrownBy(() -> DittoTracing.init(tracingConfigMock))
                .withMessage(
                        "%s was already initialized. Please ensure that initialization is only performed once.",
                        DittoTracing.class.getSimpleName()
                )
                .withNoCause();
    }

    @Test
    public void newPreparedSpanBeforeInitThrowsIllegalStateException() {
        assertThatIllegalStateException()
                .isThrownBy(() -> DittoTracing.newPreparedSpan(
                        Map.of(),
                        SpanOperationName.of(testName.getMethodName())
                ))
                .withMessage("Operation not allowed in uninitialized state.")
                .withNoCause();
    }

    @Test
    public void newPreparedSpanWithNullHeadersThrowsNullPointerException() {
        DittoTracing.init(tracingConfigMock);

        assertThatNullPointerException()
                .isThrownBy(() -> DittoTracing.newPreparedSpan(null, SpanOperationName.of(testName.getMethodName())))
                .withMessage("The headers must not be null!")
                .withNoCause();
    }

    @Test
    public void newPreparedSpanWithNullOperationNameThrowsNullPointerException() {
        DittoTracing.init(tracingConfigMock);

        assertThatNullPointerException()
                .isThrownBy(() -> DittoTracing.newPreparedSpan(Map.of(), null))
                .withMessage("The operationName must not be null!")
                .withNoCause();
    }

    @Test
    public void newStartedSpanByTimerBeforeInitThrowsIllegalStateException() {
        assertThatIllegalStateException()
                .isThrownBy(() -> DittoTracing.newStartedSpanByTimer(
                        Map.of(),
                        Timers.newTimer(testName.getMethodName()).start()
                ))
                .withMessage("Operation not allowed in uninitialized state.")
                .withNoCause();
    }

    @Test
    public void newStartedSpanByTimerWithNullHeadersThrowsNullPointerException() {
        DittoTracing.init(tracingConfigMock);
        final var startedTimer = Timers.newTimer(testName.getMethodName()).start();

        assertThatNullPointerException()
                .isThrownBy(() -> DittoTracing.newStartedSpanByTimer(null, startedTimer))
                .withMessage("The headers must not be null!")
                .withNoCause();
    }

    @Test
    public void newStartedSpanByTimerWithNullStartedTimerThrowsNullPointerException() {
        DittoTracing.init(tracingConfigMock);

        assertThatNullPointerException()
                .isThrownBy(() -> DittoTracing.newStartedSpanByTimer(Map.of(), null))
                .withMessage("The startedTimer must not be null!")
                .withNoCause();
    }

    @Test
    public void newPreparedSpanWhenTracingIsDisabledReturnsEmptyPreparedSpan() {
        disableDittoTracingByConfig();
        DittoTracing.init(tracingConfigMock);
        final var operationName = SpanOperationName.of(testName.getMethodName());

        final var preparedSpan = DittoTracing.newPreparedSpan(Map.of(), operationName);

        assertThat(preparedSpan).isEqualTo(TracingSpans.emptyPreparedSpan(operationName));
    }

    private void disableDittoTracingByConfig() {
        Mockito.when(tracingConfigMock.isTracingEnabled()).thenReturn(false);
    }

    @Test
    public void newStartedSpanByTimerWhenTracingIsDisabledReturnsEmptyPreparedSpan() {
        disableDittoTracingByConfig();
        DittoTracing.init(tracingConfigMock);
        final var traceOperationName = SpanOperationName.of(testName.getMethodName());
        final var startedTimer = Timers.newTimer(traceOperationName.toString()).start();

        final var startedSpan = DittoTracing.newStartedSpanByTimer(Map.of(), startedTimer);

        assertThat(startedSpan).isEqualTo(TracingSpans.emptyStartedSpan(traceOperationName));
    }

    @Test
    public void newPreparedSpanWhenTracingIsEnabledReturnsExpectedPreparedSpan() {
        DittoTracing.init(tracingConfigMock);
        final var correlationId = testName.getMethodName();
        final var connectionId = "my-connection";
        final var entityId = "my-entity";
        final var retainedHeaders = Map.of(
                DittoHeaderDefinition.CORRELATION_ID.getKey(), correlationId,
                DittoHeaderDefinition.CONNECTION_ID.getKey(), connectionId,
                DittoHeaderDefinition.ENTITY_ID.getKey(), entityId
        );
        final var allHeaders = new HashMap<>(retainedHeaders);
        allHeaders.put("foo", "bar");
        allHeaders.put("ping", "pong");
        final var operationName = SpanOperationName.of(correlationId);

        final var preparedSpan = DittoTracing.newPreparedSpan(allHeaders, operationName);

        try (final var softly = new AutoCloseableSoftAssertions()) {
            softly.assertThat(preparedSpan.getTagSet())
                    .as("tags")
                    .containsOnly(
                            SpanTagKey.CORRELATION_ID.getTagForValue(correlationId),
                            SpanTagKey.CONNECTION_ID.getTagForValue(connectionId),
                            SpanTagKey.ENTITY_ID.getTagForValue(entityId)
                    );
            softly.assertThat(preparedSpan.start())
                    .satisfies(startedSpan -> softly.assertThat((CharSequence) startedSpan.getOperationName())
                            .as("operation name")
                            .isEqualTo(operationName));
        }
    }

    @Test
    public void newStartedSpanByTimerWhenTracingIsEnabledReturnsExpectedStartedSpan() {
        DittoTracing.init(tracingConfigMock);
        final var testMethodName = testName.getMethodName();
        final var operationName = SpanOperationName.of(testMethodName);
        final var timerTags = TagSet.ofTagCollection(List.of(Tag.of("foo", "bar"), Tag.of("marco", "polo")));
        final var startedTimer = Timers.newTimer(operationName.toString()).tags(timerTags).start();
        final var dittoHeaders = DittoHeaders.newBuilder().correlationId(operationName).build();
        final var testSpanReporter = testSpanReporterResource.registerTestSpanReporter(testMethodName);

        final var startedSpan = DittoTracing.newStartedSpanByTimer(dittoHeaders, startedTimer);

        final var finishedSpanFuture = testSpanReporter.getFinishedSpanForSpanWithId(startedSpan.getSpanId());
        startedTimer.stop();

        assertThat(finishedSpanFuture)
                .succeedsWithin(Duration.ofSeconds(2L))
                .satisfies(finishedSpan -> {
                    assertThat(KamonTagSetConverter.getDittoTagSet(finishedSpan.tags()))
                            .as("tags")
                            .containsAll(timerTags)
                            .contains(
                                    SpanTagKey.CORRELATION_ID.getTagForValue(
                                            dittoHeaders.getCorrelationId().orElseThrow()
                                    )
                            );
                    assertThat(finishedSpan.from())
                            .as("start instant")
                            .isEqualTo(startedTimer.getStartInstant().toInstant());
                });
    }

    @Test
    public void newPreparedSpanWithFilteredOperationNameReturnsEmptyPreparedSpan() {
        final var tracingConfig = DefaultTracingConfig.of(ConfigFactory.parseMap(Map.of(
                "tracing",
                Map.of(
                        "enabled", true,
                        "propagation-channel", "default",
                        "filter", Map.of("includes", List.of("*"), "excludes", List.of("some-operation"))
                )
        )));
        DittoTracing.init(tracingConfig);
        final var operationName = SpanOperationName.of("some-operation");

        final var preparedSpan = DittoTracing.newPreparedSpan(Map.of(), operationName);

        assertThat(preparedSpan).isEqualTo(TracingSpans.emptyPreparedSpan(operationName));
    }

}