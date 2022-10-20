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
import java.util.Map;

import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import kamon.Kamon;
import kamon.trace.Identifier;
import kamon.trace.Span;

/**
 * Unit test for {@link StartedKamonSpan}.
 */
public final class StartedKamonSpanTest {

    @ClassRule
    public static final KamonTracingInitResource KAMON_TRACING_INIT_RESOURCE = KamonTracingInitResource.newInstance(
            KamonTracingInitResource.KamonTracingConfig.defaultValues()
    );

    private static final Identifier TRACE_ID = Kamon.identifierScheme().traceIdFactory().generate();

    private static Span rootSpan;

    @Rule
    public final TestName testName = new TestName();

    private StartedSpan underTest;

    @BeforeClass
    public static void beforeClass() {
        rootSpan = Kamon.spanBuilder("/").traceId(TRACE_ID).start();
    }

    @Before
    public void setup() {
        underTest = StartedKamonSpan.newInstance(
                Kamon.spanBuilder(testName.getMethodName()).asChildOf(rootSpan).start(),
                KamonHttpContextPropagation.newInstanceForChannelName("default")
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
        return MessageFormat.format("00-{0}-{1}-00", TRACE_ID.string(), underTest.getSpanId());
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
