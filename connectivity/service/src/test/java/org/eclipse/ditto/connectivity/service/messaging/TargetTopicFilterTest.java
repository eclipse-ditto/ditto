/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.connectivity.model.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.service.messaging.TargetTopicFilter.ParsedTopicFilter;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.events.ThingModified;
import org.junit.Test;

/**
 * Tests {@link TargetTopicFilter}.
 */
public final class TargetTopicFilterTest {

    private static final ConnectionId CONNECTION_ID = ConnectionId.generateRandom();
    private static final ThingId THING_ID = ThingId.of("foo:bar13");

    // ===== parse(): pure forms =====

    @Test
    public void parsePurePipelineFilter() {
        final ParsedTopicFilter parsed = TargetTopicFilter.parse("fn:filter(header:ditto-originator,'eq','x')");

        assertThat(parsed.getRqlExpression()).isEmpty();
        assertThat(parsed.getPipelineExpression()).contains("fn:filter(header:ditto-originator,'eq','x')");
    }

    @Test
    public void parsePureRqlFilters() {
        assertPureRql("and(eq(attributes/a,1),eq(attributes/b,2))");
        assertPureRql("eq(attributes/a,1)");
        assertPureRql("exists(attributes/a)");
    }

    private static void assertPureRql(final String rql) {
        final ParsedTopicFilter parsed = TargetTopicFilter.parse(rql);

        assertThat(parsed.getRqlExpression()).contains(rql);
        assertThat(parsed.getPipelineExpression()).isEmpty();
    }

    // ===== parse(): combined forms =====

    @Test
    public void parseCombinedRqlAndSinglePipelineStage() {
        final ParsedTopicFilter parsed =
                TargetTopicFilter.parse("gt(attributes/x,5)|fn:filter(header:ditto-originator,'eq','x')");

        assertThat(parsed.getRqlExpression()).contains("gt(attributes/x,5)");
        assertThat(parsed.getPipelineExpression()).contains("fn:filter(header:ditto-originator,'eq','x')");
    }

    @Test
    public void parseCombinedRqlAndTwoStagePipeline() {
        final ParsedTopicFilter parsed =
                TargetTopicFilter.parse("gt(attributes/x,5)|fn:filter(header:a,'exists')|fn:filter(header:b,'ne','x')");

        assertThat(parsed.getRqlExpression()).contains("gt(attributes/x,5)");
        assertThat(parsed.getPipelineExpression())
                .contains("fn:filter(header:a,'exists')|fn:filter(header:b,'ne','x')");
    }

    // ===== parse(): back-compat regressions (D-C1) — unquoted "|" in RQL property paths =====

    @Test
    public void parseBackCompatEqWithPipeInPropertyPath() {
        assertPureRql("eq(attributes/a|b,1)");
    }

    @Test
    public void parseBackCompatExistsWithPipeInPropertyPath() {
        assertPureRql("exists(attributes/a|b)");
    }

    @Test
    public void parseBackCompatLikeWithPipeInQuotedPattern() {
        assertPureRql("like(attributes/x,'*|*')");
    }

    @Test
    public void parseBackCompatLikeWithFnAnchorInsideQuotes() {
        // the "|fn:x*" substring is inside a quoted RQL string literal, so it must NOT be treated as an anchor
        assertPureRql("like(attributes/a,'*|fn:x*')");
    }

    @Test
    public void parseLoudEdgeCaseSplitsOnUnquotedAnchorEvenInsidePropertyPath() {
        // "eq(attributes/a|fn:b,1)": the "|" right before "fn:" is unquoted, so per the anchored-split grammar
        // this DOES split (even though the resulting RQL head is then syntactically invalid RQL) -
        // rejection of the malformed head happens later, at RQL-parse/validation time, not in parse().
        final ParsedTopicFilter parsed = TargetTopicFilter.parse("eq(attributes/a|fn:b,1)");

        assertThat(parsed.getRqlExpression()).contains("eq(attributes/a");
        assertThat(parsed.getPipelineExpression()).contains("fn:b,1)");
    }

    // ===== parse(): backslash-escaped quotes (round-2 review finding — escaped ' must not toggle quote state) =====

    @Test
    public void parseCombinedRqlWithEscapedSingleQuoteInLiteralThenPipelineAnchor() {
        // the "\'" inside the RQL string literal must not flip the quote-parity state, so the real "|fn:" anchor
        // right after the (correctly still-open-then-closed) literal is recognized.
        final ParsedTopicFilter parsed = TargetTopicFilter.parse(
                "eq(attributes/owner,'O\\'Brien')|fn:filter(header:ditto-origin,'ne','x')");

        assertThat(parsed.getRqlExpression()).contains("eq(attributes/owner,'O\\'Brien')");
        assertThat(parsed.getPipelineExpression()).contains("fn:filter(header:ditto-origin,'ne','x')");
    }

    @Test
    public void parsePureRqlWithFnAnchorAfterEscapedQuoteStaysUnsplit() {
        // the "\'" does not close the literal, so the "|fn:b" that follows is still inside the quoted string and
        // must NOT be treated as an anchor - this is a currently-valid pure-RQL filter.
        assertPureRql("like(attributes/x,'a\\'|fn:b')");
    }

    @Test
    public void parseTrailingBackslashDoesNotThrow() {
        assertThatNoException().isThrownBy(() -> TargetTopicFilter.parse("eq(attributes/a,1) fn:b\\"));
    }

    // ===== parse(): trimming / whitespace =====

    @Test
    public void parseTrimsLeadingWhitespaceBeforeFnPrefix() {
        final ParsedTopicFilter parsed = TargetTopicFilter.parse(" fn:filter(header:ditto-originator,'eq','x')");

        assertThat(parsed.getRqlExpression()).isEmpty();
        assertThat(parsed.getPipelineExpression()).contains("fn:filter(header:ditto-originator,'eq','x')");
    }

    @Test
    public void parseCombinedFilterWithWhitespaceAroundAnchorTrimsRqlHeadAndPipelineEvaluates() {
        final ParsedTopicFilter parsed =
                TargetTopicFilter.parse("gt(attributes/x,5) | fn:filter(header:a,'exists')");

        assertThat(parsed.getRqlExpression()).contains("gt(attributes/x,5)");
        // parse() itself does not strip the whitespace between the anchor "|" and "fn:" from the pipeline part -
        // that stage-internal trimming happens later, in ImmutableExpressionResolver - so compare trimmed here.
        assertThat(parsed.getPipelineExpression().map(String::trim)).contains("fn:filter(header:a,'exists')");

        // prove the parsed (untrimmed) pipeline part still evaluates correctly end-to-end.
        final Signal<?> signalWithHeaderA = thingModifiedWithHeader("a", "present");
        assertThat(TargetTopicFilter.matchesPipelineFilter(
                parsed.getPipelineExpression().orElseThrow(), signalWithHeaderA, CONNECTION_ID)).isTrue();
    }

    // ===== parse(): empty / whitespace-only filters (null is the only "absent" marker) =====

    @Test
    public void parseEmptyFilterYieldsPresentEmptyRqlExpressionNotAbsent() {
        // regression lock: an empty filter must NOT collapse to "both parts absent" - null is the only "absent"
        // marker on ParsedTopicFilter (see its class-level documentation). A present-but-empty RQL part is what
        // lets ConnectionValidator route it into RQL validation and reject it with InvalidRqlExpressionException,
        // exactly as an empty filter was rejected before target topic pipeline filters existed.
        final ParsedTopicFilter parsed = TargetTopicFilter.parse("");

        assertThat(parsed.getRqlExpression()).contains("");
        assertThat(parsed.getPipelineExpression()).isEmpty();
    }

    @Test
    public void parseWhitespaceOnlyFilterYieldsPresentEmptyRqlExpressionNotAbsent() {
        final ParsedTopicFilter parsed = TargetTopicFilter.parse("   ");

        assertThat(parsed.getRqlExpression()).contains("");
        assertThat(parsed.getPipelineExpression()).isEmpty();
    }

    @Test
    public void parseLeadingPipeWithEmptyRqlHeadIsPurePipeline() {
        // "|fn:..." - the RQL head before the anchor is empty, so it is passed as null explicitly and the whole
        // filter is treated as a pure pipeline expression (the pipeline part itself can never be empty by
        // construction, since the anchor requires "fn:" right after the "|").
        final ParsedTopicFilter parsed = TargetTopicFilter.parse("|fn:filter(header:x,'eq','y')");

        assertThat(parsed.getRqlExpression()).isEmpty();
        assertThat(parsed.getPipelineExpression()).contains("fn:filter(header:x,'eq','y')");
    }

    // ===== parse(): malformed pipeline is not rejected at parse time =====

    @Test
    public void parseGluedGarbagePipelineIsKeptAsSingleStageListNotRejected() {
        // starts with "fn:" => the WHOLE trimmed string becomes the pipeline expression, unvalidated.
        // Rejection of "gt(x)" as an invalid function stage happens later, in validatePipelineFilter().
        final ParsedTopicFilter parsed = TargetTopicFilter.parse("fn:a(x)|gt(x)|fn:b(x)");

        assertThat(parsed.getRqlExpression()).isEmpty();
        assertThat(parsed.getPipelineExpression()).contains("fn:a(x)|gt(x)|fn:b(x)");
    }

    // ===== matchesPipelineFilter(): match / non-match against a signal =====

    @Test
    public void matchesPipelineFilterEqMatchesOnDittoOriginator() {
        final Signal<?> signal = thingModifiedWithHeader("ditto-originator", "some:subject");

        assertThat(TargetTopicFilter.matchesPipelineFilter(
                "fn:filter(header:ditto-originator,'eq','some:subject')", signal, CONNECTION_ID)).isTrue();
        assertThat(TargetTopicFilter.matchesPipelineFilter(
                "fn:filter(header:ditto-originator,'eq','other:subject')", signal, CONNECTION_ID)).isFalse();
    }

    @Test
    public void matchesPipelineFilterNeMatchesOnDittoOriginator() {
        final Signal<?> signal = thingModifiedWithHeader("ditto-originator", "some:subject");

        assertThat(TargetTopicFilter.matchesPipelineFilter(
                "fn:filter(header:ditto-originator,'ne','other:subject')", signal, CONNECTION_ID)).isTrue();
        assertThat(TargetTopicFilter.matchesPipelineFilter(
                "fn:filter(header:ditto-originator,'ne','some:subject')", signal, CONNECTION_ID)).isFalse();
    }

    @Test
    public void matchesPipelineFilterOnDittoOriginHeader() {
        final Signal<?> signal = thingModifiedWithHeader("ditto-origin", "some-origin");

        assertThat(TargetTopicFilter.matchesPipelineFilter(
                "fn:filter(header:ditto-origin,'eq','some-origin')", signal, CONNECTION_ID)).isTrue();
        assertThat(TargetTopicFilter.matchesPipelineFilter(
                "fn:filter(header:ditto-origin,'eq','other-origin')", signal, CONNECTION_ID)).isFalse();
    }

    @Test
    public void matchesPipelineFilterChainedTwoStagePipeline() {
        final String chained = "fn:filter(header:a,'exists')|fn:filter(header:b,'ne','x')";

        final Signal<?> bothConditionsHold = thingModifiedWithHeaders(Map.of("a", "present", "b", "y"));
        assertThat(TargetTopicFilter.matchesPipelineFilter(chained, bothConditionsHold, CONNECTION_ID)).isTrue();

        final Signal<?> secondConditionFails = thingModifiedWithHeaders(Map.of("a", "present", "b", "x"));
        assertThat(TargetTopicFilter.matchesPipelineFilter(chained, secondConditionFails, CONNECTION_ID)).isFalse();

        final Signal<?> firstConditionFails = thingModifiedWithHeaders(Map.of("b", "y"));
        assertThat(TargetTopicFilter.matchesPipelineFilter(chained, firstConditionFails, CONNECTION_ID)).isFalse();
    }

    // ===== matchesPipelineFilter(): absent-header semantics (verified facts, Fact 5) =====

    @Test
    public void matchesPipelineFilterAbsentHeaderEqDrops() {
        final Signal<?> signal = thingModifiedWithHeaders(Collections.emptyMap());

        assertThat(TargetTopicFilter.matchesPipelineFilter(
                "fn:filter(header:ditto-originator,'eq','x')", signal, CONNECTION_ID)).isFalse();
    }

    @Test
    public void matchesPipelineFilterAbsentHeaderNePublishes() {
        final Signal<?> signal = thingModifiedWithHeaders(Collections.emptyMap());

        assertThat(TargetTopicFilter.matchesPipelineFilter(
                "fn:filter(header:ditto-originator,'ne','x')", signal, CONNECTION_ID)).isTrue();
    }

    @Test
    public void matchesPipelineFilterAbsentHeaderLikeDrops() {
        final Signal<?> signal = thingModifiedWithHeaders(Collections.emptyMap());

        assertThat(TargetTopicFilter.matchesPipelineFilter(
                "fn:filter(header:ditto-originator,'like','some:*')", signal, CONNECTION_ID)).isFalse();
    }

    @Test
    public void matchesPipelineFilterAbsentHeaderTwoParamExistsDrops() {
        final Signal<?> signal = thingModifiedWithHeaders(Collections.emptyMap());

        assertThat(TargetTopicFilter.matchesPipelineFilter(
                "fn:filter(header:ditto-originator,'exists')", signal, CONNECTION_ID)).isFalse();
    }

    // ===== validatePipelineFilter(): invalid expressions =====

    @Test
    public void validatePipelineFilterRejectsUnknownFunction() {
        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class).isThrownBy(() ->
                TargetTopicFilter.validatePipelineFilter("fn:unknownfn('x')", DittoHeaders.empty()));
    }

    @Test
    public void validatePipelineFilterRejectsFilterWithoutArguments() {
        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class).isThrownBy(() ->
                TargetTopicFilter.validatePipelineFilter("fn:filter()", DittoHeaders.empty()));
    }

    @Test
    public void validatePipelineFilterRejectsUnknownPlaceholderPrefix() {
        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class).isThrownBy(() ->
                TargetTopicFilter.validatePipelineFilter("fn:filter(bogus:x,'eq','y')", DittoHeaders.empty()));
    }

    @Test
    public void validatePipelineFilterRejectsElevenUserStages() {
        final String elevenStages = IntStream.range(0, 11)
                .mapToObj(i -> "fn:filter(header:a,'exists')")
                .collect(Collectors.joining("|"));

        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class).isThrownBy(() ->
                TargetTopicFilter.validatePipelineFilter(elevenStages, DittoHeaders.empty()));
    }

    @Test
    public void validatePipelineFilterAcceptsTenUserStages() {
        final String tenStages = IntStream.range(0, 10)
                .mapToObj(i -> "fn:filter(header:a,'exists')")
                .collect(Collectors.joining("|"));

        assertThatNoException().isThrownBy(() ->
                TargetTopicFilter.validatePipelineFilter(tenStages, DittoHeaders.empty()));
    }

    // ===== test helpers =====

    private static Signal<?> thingModifiedWithHeader(final String key, final String value) {
        return thingModifiedWithHeaders(Map.of(key, value));
    }

    private static Signal<?> thingModifiedWithHeaders(final Map<String, String> headers) {
        final Thing thing = Thing.newBuilder()
                .setId(THING_ID)
                .setAttribute(JsonPointer.of("test"), JsonValue.of(42))
                .build();
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().putHeaders(headers).build();
        return ThingModified.of(thing, 1L, Instant.now(), dittoHeaders, null);
    }
}
