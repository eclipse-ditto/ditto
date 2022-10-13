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
import java.util.Map;

import org.assertj.core.api.AutoCloseableSoftAssertions;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.eclipse.ditto.internal.utils.metrics.instruments.timer.Timers;
import org.eclipse.ditto.internal.utils.tracing.config.TracingConfig;
import org.eclipse.ditto.internal.utils.tracing.instruments.trace.KamonTracingInitResource;
import org.eclipse.ditto.internal.utils.tracing.instruments.trace.TestSpanReporter;
import org.eclipse.ditto.internal.utils.tracing.instruments.trace.Traces;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.mockito.Mockito;

import kamon.Kamon;

/**
 * Unit test for {@link DittoTracing}.
 */
public final class DittoTracingTest {

    @ClassRule
    public static final KamonTracingInitResource KAMON_TRACING_INIT_RESOURCE = KamonTracingInitResource.newInstance(
            KamonTracingInitResource.KamonTracingConfig.defaultValues()
                    .withIdentifierSchemeDouble()
                    .withSamplerAlways()
                    .withTickInterval(Duration.ofMillis(100L))
    );

    @Rule
    public final TestName testName = new TestName();

    private TracingConfig tracingConfigMock;

    @Before
    public void before() {
        tracingConfigMock = Mockito.mock(TracingConfig.class);
        Mockito.when(tracingConfigMock.isTracingEnabled()).thenReturn(true);
        Mockito.when(tracingConfigMock.getPropagationChannel()).thenReturn("default");
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
    public void newPreparedTraceBeforeInitThrowsIllegalStateException() {
        assertThatIllegalStateException()
                .isThrownBy(() -> DittoTracing.newPreparedTrace(
                        Map.of(),
                        TraceOperationName.of(testName.getMethodName())
                ))
                .withMessage("Operation not allowed in uninitialized state.")
                .withNoCause();
    }

    @Test
    public void newPreparedTraceWithNullHeadersThrowsNullPointerException() {
        DittoTracing.init(tracingConfigMock);

        assertThatNullPointerException()
                .isThrownBy(() -> DittoTracing.newPreparedTrace(null, TraceOperationName.of(testName.getMethodName())))
                .withMessage("The headers must not be null!")
                .withNoCause();
    }

    @Test
    public void newPreparedTraceWithNullTraceOperationNameThrowsNullPointerException() {
        DittoTracing.init(tracingConfigMock);

        assertThatNullPointerException()
                .isThrownBy(() -> DittoTracing.newPreparedTrace(Map.of(), null))
                .withMessage("The traceOperationName must not be null!")
                .withNoCause();
    }

    @Test
    public void newTraceTraceByTimerBeforeInitThrowsIllegalStateException() {
        assertThatIllegalStateException()
                .isThrownBy(() -> DittoTracing.newStartedTraceByTimer(
                        Map.of(),
                        Timers.newTimer(testName.getMethodName()).start()
                ))
                .withMessage("Operation not allowed in uninitialized state.")
                .withNoCause();
    }

    @Test
    public void newStartedTraceByTimerWithNullHeadersThrowsNullPointerException() {
        DittoTracing.init(tracingConfigMock);
        final var startedTimer = Timers.newTimer(testName.getMethodName()).start();

        assertThatNullPointerException()
                .isThrownBy(() -> DittoTracing.newStartedTraceByTimer(null, startedTimer))
                .withMessage("The headers must not be null!")
                .withNoCause();
    }

    @Test
    public void newStartedTraceByTimerWithNullStartedTimerThrowsNullPointerException() {
        DittoTracing.init(tracingConfigMock);

        assertThatNullPointerException()
                .isThrownBy(() -> DittoTracing.newStartedTraceByTimer(Map.of(), null))
                .withMessage("The startedTimer must not be null!")
                .withNoCause();
    }

    @Test
    public void newPreparedTraceWhenTracingIsDisabledReturnsEmptyPreparedTrace() {
        disableDittoTracingByConfig();
        DittoTracing.init(tracingConfigMock);
        final var operationName = TraceOperationName.of(testName.getMethodName());

        final var preparedTrace = DittoTracing.newPreparedTrace(Map.of(), operationName);

        assertThat(preparedTrace).isEqualTo(Traces.emptyPreparedTrace(operationName));
    }

    private void disableDittoTracingByConfig() {
        Mockito.when(tracingConfigMock.isTracingEnabled()).thenReturn(false);
    }

    @Test
    public void newStartedTraceByTimerWhenTracingIsDisabledReturnsEmptyPreparedTrace() {
        disableDittoTracingByConfig();
        DittoTracing.init(tracingConfigMock);
        final var traceOperationName = TraceOperationName.of(testName.getMethodName());
        final var startedTimer = Timers.newTimer(traceOperationName.toString()).start();

        final var startedTrace = DittoTracing.newStartedTraceByTimer(Map.of(), startedTimer);

        assertThat(startedTrace).isEqualTo(Traces.emptyStartedTrace(traceOperationName));
    }

    @Test
    public void newPreparedTraceWhenTracingIsEnabledReturnsExpectedPreparedTrace() {
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
        final var operationName = TraceOperationName.of(correlationId);

        final var preparedTrace = DittoTracing.newPreparedTrace(allHeaders, operationName);

        try (final var softly = new AutoCloseableSoftAssertions()) {
            softly.assertThat(preparedTrace.getTags())
                    .as("tags")
                    .containsOnly(
                            Map.entry(TracingTags.CORRELATION_ID, correlationId),
                            Map.entry(TracingTags.CONNECTION_ID, connectionId),
                            Map.entry(TracingTags.ENTITY_ID, entityId)
                    );
            softly.assertThat(preparedTrace.start())
                    .satisfies(startedTrace -> softly.assertThat((CharSequence) startedTrace.getOperationName())
                            .as("operation name")
                            .isEqualTo(operationName));
        }
    }

    @Test
    public void newStartedTraceByTimerWhenTracingIsEnabledReturnsExpectedStartedTrace() {
        DittoTracing.init(tracingConfigMock);
        final var operationName = TraceOperationName.of(testName.getMethodName());
        final var timerTags = Map.of(
                "foo", "bar",
                "marco", "polo"
        );
        final var startedTimer = Timers.newTimer(operationName.toString()).tags(timerTags).start();
        final var dittoHeaders = DittoHeaders.newBuilder().correlationId(operationName).build();
        final var spanReporter = TestSpanReporter.newInstance();
        Kamon.addReporter("mySpanReporter", spanReporter);

        final var startedTrace = DittoTracing.newStartedTraceByTimer(dittoHeaders, startedTimer);

        final var tagsForSpanFuture = spanReporter.getTagsForSpanWithId(startedTrace.getSpanId());
        final var startInstantForSpanFuture = spanReporter.getStartInstantForSpanWithId(startedTrace.getSpanId());
        startedTimer.stop();

        try (final var softly = new AutoCloseableSoftAssertions()) {
            softly.assertThat((CharSequence) startedTrace.getOperationName())
                    .as("operation name")
                    .isEqualTo(operationName);
            softly.assertThat(tagsForSpanFuture)
                    .as("tags")
                    .succeedsWithin(Duration.ofSeconds(2L))
                    .satisfies(actualTags -> {
                        softly.assertThat(actualTags)
                                .containsAllEntriesOf(timerTags)
                                .containsEntry(
                                        TracingTags.CORRELATION_ID,
                                        dittoHeaders.getCorrelationId().orElseThrow()
                                );
                    });
            softly.assertThat(startInstantForSpanFuture)
                    .as("start instant")
                    .succeedsWithin(Duration.ofSeconds(2L))
                    .isEqualTo(startedTimer.getStartInstant().toInstant());
        }
    }

}