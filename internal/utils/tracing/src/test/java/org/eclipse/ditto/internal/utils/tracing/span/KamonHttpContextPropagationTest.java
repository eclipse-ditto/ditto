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
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import kamon.Kamon;
import kamon.context.Context;
import kamon.tag.TagSet;
import kamon.trace.Identifier;
import kamon.trace.Span;

/**
 * Unit test for {@link KamonHttpContextPropagation}.
 */
public final class KamonHttpContextPropagationTest {

    @ClassRule
    public static final KamonTracingInitResource KAMON_TRACING_INIT_RESOURCE = KamonTracingInitResource.newInstance(
            KamonTracingInitResource.KamonTracingConfig.defaultValues()
    );

    private static final String CONTEXT_TAGS_KEY = "context-tags";
    private static final String TRACEPARENT_KEY = DittoHeaderDefinition.W3C_TRACEPARENT.getKey();

    private static Identifier traceId;
    private static Identifier.Factory spanIdFactory;

    @Rule
    public final TestName testName = new TestName();

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void beforeClass() {
        final var identifierScheme = Kamon.identifierScheme();
        final var traceIdFactory = identifierScheme.traceIdFactory();
        traceId = traceIdFactory.generate();
        spanIdFactory = identifierScheme.spanIdFactory();
    }

    @Test
    public void newInstanceForNullChannelNameThrowsNullPointerException() {
        assertThatNullPointerException()
                .isThrownBy(() -> KamonHttpContextPropagation.newInstanceForChannelName(null))
                .withMessage("The propagationChannelName must not be null!")
                .withNoCause();
    }

    @Test
    public void newInstanceForUnknownChannelNameReturnsFailedWithIllegalArgumentException() {
        final var channelName = "zoeglfrex";
        final var kamonHttpContextPropagationTry = KamonHttpContextPropagation.newInstanceForChannelName(channelName);

        assertThatIllegalArgumentException()
                .isThrownBy(kamonHttpContextPropagationTry::orElseThrow)
                .withMessage("HTTP propagation for channel name <%s> is undefined.", channelName)
                .withNoCause();
    }

    @Test
    public void getContextFromHeadersForNullHeadersThrowsNullPointerException() {
        final var underTest = KamonHttpContextPropagation.getInstanceForDefaultHttpChannel();

        assertThatNullPointerException()
                .isThrownBy(() -> underTest.getContextFromHeaders(null))
                .withMessage("The headers must not be null!")
                .withNoCause();
    }

    @Test
    public void getContextFromEmptyMapReturnsEmptyContext() {
        final var underTest = KamonHttpContextPropagation.getInstanceForDefaultHttpChannel();

        final var context = underTest.getContextFromHeaders(Map.of());

        assertThat(context.isEmpty()).isTrue();
    }

    @Test
    public void getContextFromMapWithCustomHeadersReturnsExpectedContext() {
        final Map<String, Object> customHeaders = Map.of(
                "foo", "bar",
                "bar", "baz",
                "marco", "polo"
        );
        final var spanId = spanIdFactory.generate();
        final var underTest = KamonHttpContextPropagation.getInstanceForDefaultHttpChannel();

        final var context = underTest.getContextFromHeaders(
                Map.of(
                        CONTEXT_TAGS_KEY, getAsContextTagsHeaderValue(customHeaders),
                        "ignored_key", "ignored_value",
                        TRACEPARENT_KEY, getAsTraceparentHeaderValue(traceId, spanId)
                )
        );

        softly.assertThat(context.tags()).as("tags").isEqualTo(TagSet.from(customHeaders));
        softly.assertThat(context.get(Span.Key()))
                .satisfies(span -> {
                    softly.assertThat(span.id()).as("span Id").isEqualTo(spanId);
                    softly.assertThat(span.trace())
                            .satisfies(trace -> assertThat(trace.id()).as("trace ID").isEqualTo(traceId));
                });
    }

    private static String getAsContextTagsHeaderValue(final Map<String, Object> map) {
        return map.entrySet()
                .stream()
                .map(entry -> MessageFormat.format("{0}={1}", entry.getKey(), entry.getValue()))
                .collect(Collectors.joining(";"));
    }

    private static String getAsTraceparentHeaderValue(final Identifier traceId, final Identifier spanId) {
        return MessageFormat.format("00-{0}-{1}-00", traceId.string(), spanId.string());
    }

    @Test
    public void propagateContextToHeadersWithNullContextThrowsNullPointerException() {
        final var underTest = KamonHttpContextPropagation.getInstanceForDefaultHttpChannel();

        assertThatNullPointerException()
                .isThrownBy(() -> underTest.propagateContextToHeaders(null, Map.of()))
                .withMessage("The context must not be null!")
                .withNoCause();
    }

    @Test
    public void propagateContextToHeadersWithNullMapThrowsNullPointerException() {
        final var underTest = KamonHttpContextPropagation.getInstanceForDefaultHttpChannel();

        assertThatNullPointerException()
                .isThrownBy(() -> underTest.propagateContextToHeaders(Context.Empty(), null))
                .withMessage("The headers must not be null!")
                .withNoCause();
    }

    @Test
    public void propagateContextToMapReturnsExpectedMap() {
        final Map<String, Object> contextTags = Map.of(
                "foo", "bar",
                "bar", "baz",
                "marco", "polo"
        );
        final var span = Kamon.spanBuilder(testName.getMethodName()).traceId(traceId).start();
        final var dittoHeaders = DittoHeaders.newBuilder().correlationId(testName.getMethodName()).build();
        final var underTest = KamonHttpContextPropagation.getInstanceForDefaultHttpChannel();

        final var headersWithPropagatedContext = underTest.propagateContextToHeaders(
                Context.of(Span.Key(), span, TagSet.from(contextTags)),
                dittoHeaders
        );

        softly.assertThat(headersWithPropagatedContext)
                .as("result map")
                .hasSize(dittoHeaders.size() + 3)
                .contains(
                        Map.entry(DittoHeaderDefinition.CORRELATION_ID.getKey(), testName.getMethodName()),
                        Map.entry(TRACEPARENT_KEY, getAsTraceparentHeaderValue(traceId, span.id())),
                        Map.entry("tracestate", "")
                )
                .containsKey(CONTEXT_TAGS_KEY);
        softly.assertThat(parseContextTagsHeaderValueToMap(headersWithPropagatedContext.get(CONTEXT_TAGS_KEY)))
                .as("context-tags")
                .hasSize(contextTags.size() + 1)
                .containsAllEntriesOf(contextTags)
                .containsEntry("upstream.name", "kamon-application");
    }

    private static Map<String, Object> parseContextTagsHeaderValueToMap(final String contextTagsHeaderValue) {
        return Arrays.stream(contextTagsHeaderValue.split(";"))
                .map(tagHeaderEntry -> {
                    final var tagHeaderEntryParts = tagHeaderEntry.split("=");
                    return Map.entry(tagHeaderEntryParts[0], tagHeaderEntryParts[1]);
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

}