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
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.connectivity.model.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.connectivity.model.ConnectionId;
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

    // ===== isFunctionExpression(): classification of a raw parameter value =====

    @Test
    public void isFunctionExpressionDetectsLeadingFnPrefixOnly() {
        assertThat(TargetTopicFilter.isFunctionExpression("fn:filter(header:a,'exists')")).isTrue();
        assertThat(TargetTopicFilter.isFunctionExpression(" fn:filter(header:a,'exists')")).isTrue();
        assertThat(TargetTopicFilter.isFunctionExpression("header:a|fn:filter('ne','x')")).isFalse();
        assertThat(TargetTopicFilter.isFunctionExpression("gt(attributes/x,5)")).isFalse();
        // an "fn:" substring anywhere but the (trimmed) start does not count
        assertThat(TargetTopicFilter.isFunctionExpression("like(attributes/a,'*|fn:x*')")).isFalse();
        assertThat(TargetTopicFilter.isFunctionExpression("")).isFalse();
    }

    @Test
    public void isPipelineExpressionDetectsFunctionFirstAndPlaceholderFirstPipelines() {
        assertThat(TargetTopicFilter.isPipelineExpression("fn:filter(header:a,'exists')")).isTrue();
        assertThat(TargetTopicFilter.isPipelineExpression("header:a|fn:filter('ne','x')")).isTrue();
        assertThat(TargetTopicFilter.isPipelineExpression(
                "  thing-json:attributes/a | fn:lower()|fn:filter('ne','x')")).isTrue();
        // RQL - also with an unquoted '|' in a property path, a quoted "|fn:" or the retired "<rql>|fn:..." syntax
        assertThat(TargetTopicFilter.isPipelineExpression("gt(attributes/x,5)")).isFalse();
        assertThat(TargetTopicFilter.isPipelineExpression("eq(attributes/a|b,1)")).isFalse();
        assertThat(TargetTopicFilter.isPipelineExpression("like(attributes/a,'*|fn:x*')")).isFalse();
        assertThat(TargetTopicFilter.isPipelineExpression("gt(attributes/x,5)|fn:filter(header:a,'exists')"))
                .isFalse();
        assertThat(TargetTopicFilter.isPipelineExpression("")).isFalse();
    }

    // ===== matchesFnFilter(): match / non-match against a signal =====

    @Test
    public void matchesFnFilterEqMatchesOnDittoOriginator() {
        final Signal<?> signal = thingModifiedWithHeader("ditto-originator", "some:subject");

        assertThat(TargetTopicFilter.matchesFnFilter(
                "header:ditto-originator|fn:filter('eq','some:subject')", signal, CONNECTION_ID)).isTrue();
        assertThat(TargetTopicFilter.matchesFnFilter(
                "header:ditto-originator|fn:filter('eq','other:subject')", signal, CONNECTION_ID)).isFalse();
    }

    @Test
    public void matchesFnFilterNeMatchesOnDittoOriginator() {
        final Signal<?> signal = thingModifiedWithHeader("ditto-originator", "some:subject");

        assertThat(TargetTopicFilter.matchesFnFilter(
                "header:ditto-originator|fn:filter('ne','other:subject')", signal, CONNECTION_ID)).isTrue();
        assertThat(TargetTopicFilter.matchesFnFilter(
                "header:ditto-originator|fn:filter('ne','some:subject')", signal, CONNECTION_ID)).isFalse();
    }

    @Test
    public void matchesFnFilterOnDittoOriginHeader() {
        final Signal<?> signal = thingModifiedWithHeader("ditto-origin", "some-origin");

        assertThat(TargetTopicFilter.matchesFnFilter(
                "header:ditto-origin|fn:filter('eq','some-origin')", signal, CONNECTION_ID)).isTrue();
        assertThat(TargetTopicFilter.matchesFnFilter(
                "header:ditto-origin|fn:filter('eq','other-origin')", signal, CONNECTION_ID)).isFalse();
    }

    @Test
    public void matchesFnFilterLikeUsesWildcardPattern() {
        final Signal<?> signal = thingModifiedWithHeader("ditto-originator", "integration:solution:conn1");

        assertThat(TargetTopicFilter.matchesFnFilter(
                "header:ditto-originator|fn:filter('like','integration:*')", signal, CONNECTION_ID)).isTrue();
        assertThat(TargetTopicFilter.matchesFnFilter(
                "header:ditto-originator|fn:filter('like','nginx:*')", signal, CONNECTION_ID)).isFalse();
    }

    @Test
    public void matchesFnFilterExistsTruePublishesExactlyWhenThePlaceholderResolves() {
        final String fnFilter = "header:ditto-originator|fn:filter('exists','true')";

        assertThat(TargetTopicFilter.matchesFnFilter(fnFilter,
                thingModifiedWithHeader("ditto-originator", "some:subject"), CONNECTION_ID)).isTrue();
        assertThat(TargetTopicFilter.matchesFnFilter(fnFilter,
                thingModifiedWithHeaders(Collections.emptyMap()), CONNECTION_ID)).isFalse();
    }

    @Test
    public void matchesFnFilterComparedValueMayBeAPlaceholder() {
        final String fnFilter = "header:ditto-originator|fn:filter('eq',header:expected)";

        assertThat(TargetTopicFilter.matchesFnFilter(fnFilter,
                thingModifiedWithHeaders(Map.of("ditto-originator", "some:subject", "expected", "some:subject")),
                CONNECTION_ID)).isTrue();
        assertThat(TargetTopicFilter.matchesFnFilter(fnFilter,
                thingModifiedWithHeaders(Map.of("ditto-originator", "some:subject", "expected", "other:subject")),
                CONNECTION_ID)).isFalse();
    }

    @Test
    public void matchesFnFilterAbsentComparedPlaceholderDrops() {
        // no trap on the compared side either: an absent compared value drops for every rqlFunction
        final Signal<?> signal = thingModifiedWithHeader("ditto-originator", "some:subject");

        assertThat(TargetTopicFilter.matchesFnFilter(
                "header:ditto-originator|fn:filter('ne',header:absent)", signal, CONNECTION_ID)).isFalse();
        assertThat(TargetTopicFilter.matchesFnFilter(
                "header:ditto-originator|fn:filter('eq',header:absent)", signal, CONNECTION_ID)).isFalse();
    }

    @Test
    public void matchesFnFilterUnknownRqlFunctionNameNeverMatches() {
        // the runtime fact that motivates rejecting unknown literal rqlFunction names in validateFnFilter
        final Signal<?> signal = thingModifiedWithHeader("ditto-originator", "some:subject");

        assertThat(TargetTopicFilter.matchesFnFilter(
                "header:ditto-originator|fn:filter('nope','some:subject')", signal, CONNECTION_ID)).isFalse();
        assertThat(TargetTopicFilter.matchesFnFilter(
                "header:ditto-originator|fn:filter('nope','other:subject')", signal, CONNECTION_ID)).isFalse();
    }

    @Test
    public void matchesFnFilterLiteralComparedValueExistsIsTakenAsTheExistsForm() {
        // the runtime fact that motivates rejecting the literal compared value 'exists' in validateFnFilter:
        // fn:filter('ne','exists') is parsed as fn:filter(<filterValue>,'exists') with the constant 'ne' as filter
        // value, i.e. it matches for EVERY resolved carrier - even one that equals 'exists'
        assertThat(TargetTopicFilter.matchesFnFilter("header:ditto-originator|fn:filter('ne','exists')",
                thingModifiedWithHeader("ditto-originator", "exists"), CONNECTION_ID)).isTrue();
    }

    @Test
    public void matchesFnFilterFunctionFirstNeverMatches() {
        // validateFnFilter rejects the function-first form; should such an expression reach the runtime anyway, it
        // fails closed: without a leading placeholder there is no resolved carrier value an fn:filter could keep
        final Signal<?> signal = thingModifiedWithHeader("ditto-originator", "some:subject");

        assertThat(TargetTopicFilter.matchesFnFilter(
                "fn:filter(header:ditto-originator,'eq','some:subject')", signal, CONNECTION_ID)).isFalse();
        assertThat(TargetTopicFilter.matchesFnFilter(
                "fn:filter(header:ditto-originator,'ne','other:subject')", signal, CONNECTION_ID)).isFalse();
        assertThat(TargetTopicFilter.matchesFnFilter(
                "fn:filter(header:absent,'ne','x')", signal, CONNECTION_ID)).isFalse();
    }

    // ===== matchesFnFilter(): absent-header semantics =====

    @Test
    public void matchesFnFilterAbsentHeaderAlwaysDrops() {
        // an absent leading placeholder never resolves, so the fn:filter never matches - whatever the rqlFunction
        final Signal<?> signal = thingModifiedWithHeaders(Collections.emptyMap());

        for (final String fnFilter : List.of(
                "header:ditto-originator|fn:filter('ne','x')",
                "header:ditto-originator|fn:filter('eq','x')",
                "header:ditto-originator|fn:filter('like','*')",
                "header:ditto-originator|fn:filter('exists','true')",
                "header:ditto-originator|fn:filter('exists','false')")) {
            assertThat(TargetTopicFilter.matchesFnFilter(fnFilter, signal, CONNECTION_ID)).as(fnFilter).isFalse();
        }
    }

    @Test
    public void matchesFnFilterDefaultOptsInToPublishingOnAbsentHeader() {
        // an fn:default before the fn:filter supplies a value for an absent header - the explicit opt-in for
        // "publish when absent" (eq on the default) and "publish when absent or not equal" (ne)
        final Signal<?> absent = thingModifiedWithHeaders(Collections.emptyMap());
        final Signal<?> excluded = thingModifiedWithHeader("ditto-originator", "excluded:subject");
        final Signal<?> other = thingModifiedWithHeader("ditto-originator", "someone:else");

        final String absentOnly = "header:ditto-originator|fn:default('none')|fn:filter('eq','none')";
        assertThat(TargetTopicFilter.matchesFnFilter(absentOnly, absent, CONNECTION_ID)).isTrue();
        assertThat(TargetTopicFilter.matchesFnFilter(absentOnly, other, CONNECTION_ID)).isFalse();

        final String absentOrNotEqual =
                "header:ditto-originator|fn:default('none')|fn:filter('ne','excluded:subject')";
        assertThat(TargetTopicFilter.matchesFnFilter(absentOrNotEqual, absent, CONNECTION_ID)).isTrue();
        assertThat(TargetTopicFilter.matchesFnFilter(absentOrNotEqual, other, CONNECTION_ID)).isTrue();
        assertThat(TargetTopicFilter.matchesFnFilter(absentOrNotEqual, excluded, CONNECTION_ID)).isFalse();
    }

    @Test
    public void matchesFnFilterDefaultWithPlaceholderCoalescesHeaders() {
        // OR for presence: fn:default(<placeholder>) falls back to the second header when the first is absent
        final String fnFilter = "header:first|fn:default(header:second)|fn:filter('exists','true')";

        assertThat(TargetTopicFilter.matchesFnFilter(fnFilter,
                thingModifiedWithHeader("first", "a"), CONNECTION_ID)).isTrue();
        assertThat(TargetTopicFilter.matchesFnFilter(fnFilter,
                thingModifiedWithHeader("second", "b"), CONNECTION_ID)).isTrue();
        assertThat(TargetTopicFilter.matchesFnFilter(fnFilter,
                thingModifiedWithHeaders(Map.of("first", "a", "second", "b")), CONNECTION_ID)).isTrue();
        assertThat(TargetTopicFilter.matchesFnFilter(fnFilter,
                thingModifiedWithHeaders(Collections.emptyMap()), CONNECTION_ID)).isFalse();
    }

    @Test
    public void matchesFnFilterCoalescedHeadersFilterTheFirstPresentOneOnly() {
        // a coalesce, not a general OR: the second header is only looked at when the first is absent
        final String fnFilter = "header:first|fn:default(header:second)|fn:filter('eq','x')";

        assertThat(TargetTopicFilter.matchesFnFilter(fnFilter,
                thingModifiedWithHeader("second", "x"), CONNECTION_ID)).isTrue();
        assertThat(TargetTopicFilter.matchesFnFilter(fnFilter,
                thingModifiedWithHeaders(Map.of("first", "y", "second", "x")), CONNECTION_ID)).isFalse();
    }

    // ===== matchesFnFilter(): value stages before the fn:filter, other placeholders =====

    @Test
    public void matchesFnFilterAppliesValueStagesBeforeTheFilter() {
        final Signal<?> signal = thingModifiedWithHeader("ditto-originator", "Some:Subject");

        assertThat(TargetTopicFilter.matchesFnFilter(
                "header:ditto-originator|fn:lower()|fn:filter('eq','some:subject')", signal, CONNECTION_ID)).isTrue();
        assertThat(TargetTopicFilter.matchesFnFilter(
                "header:ditto-originator|fn:substring-before(':')|fn:filter('eq','Some')", signal, CONNECTION_ID))
                .isTrue();
    }

    @Test
    public void matchesFnFilterWithTopicPlaceholder() {
        // ThingModified -> topic:action resolves to "modified" (Resolvers.forSignal derives the topic path itself)
        final Signal<?> signal = thingModifiedWithHeaders(Collections.emptyMap());

        assertThat(TargetTopicFilter.matchesFnFilter(
                "topic:action|fn:filter('eq','modified')", signal, CONNECTION_ID)).isTrue();
        assertThat(TargetTopicFilter.matchesFnFilter(
                "topic:action|fn:filter('eq','created')", signal, CONNECTION_ID)).isFalse();
    }

    @Test
    public void matchesFnFilterWithThingPlaceholder() {
        final Signal<?> signal = thingModifiedWithHeaders(Collections.emptyMap());

        assertThat(TargetTopicFilter.matchesFnFilter(
                "thing:id|fn:filter('eq','foo:bar13')", signal, CONNECTION_ID)).isTrue();
        assertThat(TargetTopicFilter.matchesFnFilter(
                "thing:namespace|fn:filter('eq','other')", signal, CONNECTION_ID)).isFalse();
    }

    @Test
    public void matchesFnFilterWithThingJsonPlaceholderOnEvent() {
        // for a ThingEvent, Resolvers.forSignal feeds thing-json the event-derived thing (attribute test=42 in the
        // helper)
        final Signal<?> signal = thingModifiedWithHeaders(Collections.emptyMap());

        assertThat(TargetTopicFilter.matchesFnFilter(
                "thing-json:attributes/test|fn:filter('eq','42')", signal, CONNECTION_ID)).isTrue();
        assertThat(TargetTopicFilter.matchesFnFilter(
                "thing-json:attributes/test|fn:filter('eq','43')", signal, CONNECTION_ID)).isFalse();
    }

    @Test
    public void matchesFnFilterTrimsSurroundingWhitespace() {
        final Signal<?> signal = thingModifiedWithHeader("ditto-originator", "some:subject");

        assertThat(TargetTopicFilter.matchesFnFilter(
                "  header:ditto-originator|fn:filter('eq','some:subject')  ", signal, CONNECTION_ID)).isTrue();
    }

    @Test
    public void matchesFnFilterLeadingPlaceholderWithoutNameThrowsNonDittoException() {
        // documents the placeholder-library behavior that motivates (a) the name check in validateFnFilter and
        // (b) the runtime guards catching RuntimeException rather than DittoRuntimeException only:
        // "header:" is accepted by the grammar (ImmutableHeadersPlaceholder#supports is true for any name) but
        // resolving its value throws an IllegalArgumentException (ConditionChecker#argumentNotEmpty)
        final Signal<?> signal = thingModifiedWithHeader("ditto-originator", "some:subject");

        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                TargetTopicFilter.matchesFnFilter("header:|fn:filter('eq','x')", signal, CONNECTION_ID));
    }

    // ===== validateFnFilter(): accepted expressions =====

    @Test
    public void validateFnFilterAcceptsPlaceholderFollowedByItsOnlyFilterStage() {
        for (final String expression : List.of(
                "header:ditto-originator|fn:filter('ne','x')",
                "  header:ditto-originator|fn:filter('ne','x')  ",
                "header:ditto-originator|fn:filter('eq','x')",
                "header:ditto-originator|fn:filter('like','integration:*')",
                "header:ditto-originator|fn:filter('exists','true')",
                "header:ditto-originator|fn:filter('eq',header:expected)",
                "thing-json:attributes/test|fn:filter('eq','42')",
                "topic:action|fn:filter('eq','modified')",
                "thing:id|fn:filter('like','foo:*')")) {
            assertThatNoException()
                    .as(expression)
                    .isThrownBy(() -> TargetTopicFilter.validateFnFilter(expression, DittoHeaders.empty()));
        }
    }

    @Test
    public void validateFnFilterAcceptsValueStagesBeforeTheFilterStage() {
        for (final String expression : List.of(
                "header:ditto-originator|fn:default('none')|fn:filter('ne','x')",
                "header:first|fn:default(header:second)|fn:filter('exists','true')",
                "header:first|fn:default(header:second)|fn:default(header:third)|fn:filter('exists','true')",
                "header:ditto-originator|fn:lower()|fn:filter('eq','some:subject')",
                "header:ditto-originator|fn:substring-before(':')|fn:trim()|fn:filter('eq','some')")) {
            assertThatNoException()
                    .as(expression)
                    .isThrownBy(() -> TargetTopicFilter.validateFnFilter(expression, DittoHeaders.empty()));
        }
    }

    @Test
    public void validateFnFilterParameterSplitAgreesWithTheGrammar() {
        // the quote-aware stage/parameter split must not mis-tokenize constants containing separators or escaped
        // quotes, double-quoted constants, an empty constant or surrounding whitespace
        for (final String expression : List.of(
                "header:x|fn:filter('eq','a,b')",
                "header:x|fn:filter('eq','a|b')",
                "header:x|fn:filter('eq',\"a|b\")",
                "header:x|fn:filter('like','a|b,c')",
                "header:x|fn:filter('eq','it\\'s')",
                "header:x|fn:filter(\"ne\",\"y\")",
                "header:x|fn:filter('eq','')",
                "header:x|fn:filter( 'ne' , 'y' )",
                "header:x|fn:default('a|fn:filter(b)')|fn:filter('ne','y')")) {
            assertThatNoException()
                    .as(expression)
                    .isThrownBy(() -> TargetTopicFilter.validateFnFilter(expression, DittoHeaders.empty()));
        }
    }

    @Test
    public void validateFnFilterAcceptsTenFunctionStagesButRejectsEleven() {
        // the resolver caps a pipeline at 11 elements in total; the leading placeholder occupies one slot, leaving
        // 10 fn: stages
        final String tenStages = "header:a|" + String.join("|", Collections.nCopies(9, "fn:trim()")) +
                "|fn:filter('ne','zzz')";
        assertThatNoException().isThrownBy(() ->
                TargetTopicFilter.validateFnFilter(tenStages, DittoHeaders.empty()));

        final String elevenStages = "header:a|" + String.join("|", Collections.nCopies(10, "fn:trim()")) +
                "|fn:filter('ne','zzz')";
        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class).isThrownBy(() ->
                TargetTopicFilter.validateFnFilter(elevenStages, DittoHeaders.empty()));
    }

    // ===== validateFnFilter(): expressions rejected by the pipeline grammar =====

    @Test
    public void validateFnFilterRejectsUnknownFunctionStage() {
        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class).isThrownBy(() ->
                TargetTopicFilter.validateFnFilter("header:a|fn:unknownfn('x')|fn:filter('ne','x')",
                        DittoHeaders.empty()));
    }

    @Test
    public void validateFnFilterRejectsFilterWithoutArguments() {
        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class).isThrownBy(() ->
                TargetTopicFilter.validateFnFilter("header:a|fn:filter()", DittoHeaders.empty()));
    }

    @Test
    public void validateFnFilterRejectsUnknownPlaceholderPrefix() {
        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class).isThrownBy(() ->
                TargetTopicFilter.validateFnFilter("bogus:x|fn:filter('eq','y')", DittoHeaders.empty()));
        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class).isThrownBy(() ->
                TargetTopicFilter.validateFnFilter("header:a|fn:filter('eq',bogus:x)", DittoHeaders.empty()));
    }

    @Test
    public void validateFnFilterRejectsTrailingPipe() {
        // rejected by the resolver's pipeline grammar (empty trailing stage), no custom scan involved
        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class).isThrownBy(() ->
                TargetTopicFilter.validateFnFilter("header:a|fn:filter('ne','x')|", DittoHeaders.empty()));
    }

    @Test
    public void validateFnFilterTrailingBackslashDoesNotThrowUnexpectedly() {
        // a trailing backslash must never escape the documented exception contract; the resolver validation may
        // still reject the expression, but only ever with the documented exception type
        final Throwable throwable = catchThrowable(() ->
                TargetTopicFilter.validateFnFilter("header:a|fn:filter('ne','x')\\", DittoHeaders.empty()));

        if (throwable != null) {
            assertThat(throwable).isInstanceOf(ConnectionConfigurationInvalidException.class);
        }
    }

    @Test
    public void validateFnFilterRejectsRqlExpressionWithHintToFilterParameter() {
        final Throwable thrown = catchThrowable(() ->
                TargetTopicFilter.validateFnFilter("eq(attributes/x,1)", DittoHeaders.empty()));

        assertThat(thrown).isInstanceOf(ConnectionConfigurationInvalidException.class)
                .hasMessageContaining("'fn-filter'")
                .hasMessageContaining("The placeholder 'eq(attributes/x,1)' could not be resolved.");
        assertThat(((DittoRuntimeException) thrown).getDescription()).hasValueSatisfying(description ->
                assertThat(description).contains("RQL expressions belong into the 'filter' parameter"));
    }

    @Test
    public void validateFnFilterRejectsEmptyOrBlankExpression() {
        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class).isThrownBy(() ->
                TargetTopicFilter.validateFnFilter("", DittoHeaders.empty()));
        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class).isThrownBy(() ->
                TargetTopicFilter.validateFnFilter("   ", DittoHeaders.empty()));
    }

    @Test
    public void validateFnFilterRejectsLeadingPlaceholderWithoutName() {
        // "header:" and "thing-json:" accept ANY name at grammar level and the validation resolver never resolves
        // placeholder values, so without a dedicated check these would pass validation and throw an
        // IllegalArgumentException per signal at runtime (see matchesFnFilterLeadingPlaceholderWithoutNameThrows...).
        // "header :" (whitespace before the colon) passes the grammar with the bogus name ":" and would silently
        // never match - rejected by the same check.
        for (final String expression : List.of("header:|fn:filter('eq','x')", "header:", "  header: |fn:upper()",
                "header :|fn:filter('eq','x')", "thing-json:|fn:filter('ne','x')")) {
            assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                    .isThrownBy(() -> TargetTopicFilter.validateFnFilter(expression, DittoHeaders.empty()))
                    .withMessageContaining("has no name");
        }
    }

    // ===== validateFnFilter(): the pipeline shape - placeholder first, exactly one fn:filter, as the last stage =====

    @Test
    public void validateFnFilterRejectsFunctionFirstExpression() {
        // with the placeholder INSIDE the function an absent header is filtered as the empty value, so
        // fn:filter(header:x,'ne','v') would publish every signal lacking the header - the filtered value must be
        // the leading placeholder, which drops the signal when it does not resolve
        for (final String expression : List.of(
                "fn:filter(header:ditto-originator,'ne','x')",
                " fn:filter(header:ditto-originator,'exists')",
                "fn:filter(header:a,'eq','x')|fn:filter(header:b,'eq','y')",
                "fn:filter('ne','x')",
                "fn:default(header:a)|fn:filter('ne','x')",
                "fn:unknownfn('x')",
                "fn:upper()",
                "fn:delete()")) {
            assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                    .as(expression)
                    .isThrownBy(() -> TargetTopicFilter.validateFnFilter(expression, DittoHeaders.empty()))
                    .withMessageContaining("must start with a placeholder");
        }
    }

    @Test
    public void validateFnFilterSuggestsThePlaceholderFirstRewriteOfASimpleFunctionFirstFilter() {
        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                .isThrownBy(() -> TargetTopicFilter.validateFnFilter(
                        "fn:filter(header:ditto-originator, 'ne', 'some:subject')", DittoHeaders.empty()))
                .withMessageContaining("header:ditto-originator|fn:filter('ne','some:subject')");
        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                .isThrownBy(() -> TargetTopicFilter.validateFnFilter(
                        "fn:filter(header:ditto-originator,'exists')", DittoHeaders.empty()))
                .withMessageContaining("header:ditto-originator|fn:filter('exists','true')");
    }

    @Test
    public void validateFnFilterRejectsPipelineNotEndingWithFilterStage() {
        // the last stage must be fn:filter, the stage that yields the publish decision: a bare placeholder does not
        // filter anything (it would merely publish whenever it resolves), a trailing value-producing stage passes
        // the preceding decision through unchanged and a trailing fn:default discards it
        for (final String expression : List.of(
                "header:ditto-originator",
                " header:ditto-origin ",
                "thing-json:attributes/test",
                "topic:action",
                "header:ditto-originator|fn:upper()",
                "header:ditto-originator|fn:default('y')",
                "header:ditto-originator|fn:filter('ne','x')|fn:upper()",
                "header:ditto-originator|fn:filter('ne','x')|fn:default('y')")) {
            assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                    .as(expression)
                    .isThrownBy(() -> TargetTopicFilter.validateFnFilter(expression, DittoHeaders.empty()))
                    .withMessageContaining("the last stage must be fn:filter");
        }
    }

    @Test
    public void validateFnFilterRejectsMoreThanOneFilterStage() {
        // a second fn:filter can only test another value by taking a placeholder as its filter value - the
        // absent-header trap again; several conditions are ANDed by repeating the fn-filter parameter instead
        for (final String expression : List.of(
                "header:a|fn:filter('ne','x')|fn:filter('ne','y')",
                "header:a|fn:filter('ne','x')|fn:filter(header:b,'ne','y')",
                "header:a|fn:filter('ne','x')|fn:default('y')|fn:filter('exists','true')",
                "header:a|fn:filter('exists','true')|fn:lower()|fn:filter('eq','x')")) {
            assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                    .as(expression)
                    .isThrownBy(() -> TargetTopicFilter.validateFnFilter(expression, DittoHeaders.empty()))
                    .withMessageContaining("only one fn:filter")
                    .withMessageContaining("repeat the 'fn-filter' parameter");
        }
    }

    @Test
    public void validateFnFilterRejectsDeleteStageAtAnyPosition() {
        // fn:delete() is absorbing - no later stage can resolve a deleted pipeline again, so the topic would never
        // publish wherever the stage sits
        for (final String expression : List.of(
                "header:a|fn:delete()|fn:filter('ne','x')",
                "header:a|fn:lower()|fn:delete()|fn:filter('ne','x')",
                "header:a|fn:delete()")) {
            assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                    .as(expression)
                    .isThrownBy(() -> TargetTopicFilter.validateFnFilter(expression, DittoHeaders.empty()))
                    .withMessageContaining("no later stage can resolve");
        }
    }

    // ===== validateFnFilter(): the fn:filter stage - fn:filter('<rqlFunction>', <comparedValue>) only =====

    @Test
    public void validateFnFilterRejectsFilterStageTakingItsFilterValueAsParameter() {
        // only the 2-parameter form filtering the pipeline's own value is accepted: a filter value passed as
        // parameter is either a placeholder (absent-header trap) or a constant (never looks at the signal), and a
        // placeholder-valued rqlFunction cannot be told apart from a filter value
        for (final String expression : List.of(
                "header:a|fn:filter(header:b,'ne','x')",
                "header:a|fn:filter(header:b,'exists','false')",
                "header:a|fn:filter('a','eq','b')",
                "header:a|fn:filter(header:b,'exists')",
                "header:a|fn:filter(header:b,'eq')",
                "header:a|fn:filter(header:op,header:b)")) {
            assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                    .as(expression)
                    .isThrownBy(() -> TargetTopicFilter.validateFnFilter(expression, DittoHeaders.empty()))
                    .withMessageContaining("fn:filter('<rqlFunction>',<comparedValue>)");
        }
    }

    @Test
    public void validateFnFilterRejectsUnknownLiteralRqlFunctionName() {
        // an unknown rqlFunction name never matches at runtime -> the target would be permanently silent
        for (final String expression : List.of(
                "header:ditto-originator|fn:filter('NE','x')",
                "header:ditto-originator|fn:filter('neq','x')",
                "header:ditto-originator|fn:filter(\"nope\",'x')",
                "header:ditto-originator|fn:filter('x','exists')")) {
            assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                    .as(expression)
                    .isThrownBy(() -> TargetTopicFilter.validateFnFilter(expression, DittoHeaders.empty()))
                    .withMessageContaining("unknown rqlFunction");
        }
    }

    @Test
    public void validateFnFilterRejectsExistsFalse() {
        // the filtered value is the pipeline's own, resolved value - it always exists, so 'exists','false' would
        // silence the topic (see matchesFnFilterAbsentHeaderAlwaysDrops); "publish when absent" needs an fn:default
        for (final String expression : List.of(
                "header:ditto-originator|fn:filter('exists','false')",
                "header:ditto-originator|fn:filter('exists',\"FALSE\")")) {
            assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                    .as(expression)
                    .isThrownBy(() -> TargetTopicFilter.validateFnFilter(expression, DittoHeaders.empty()))
                    .withMessageContaining("fn:default('<none>')|fn:filter('eq','<none>')");
        }
    }

    @Test
    public void validateFnFilterRejectsLiteralComparedValueExists() {
        // fn:filter(<anything>,'exists') is the pipeline function's exists form: the first parameter would be
        // taken as a constant filter value and the stage would match for every resolved value
        for (final String expression : List.of(
                "header:ditto-originator|fn:filter('ne','exists')",
                "header:ditto-originator|fn:filter('eq',\"exists\")",
                "header:ditto-originator|fn:filter('exists','exists')")) {
            assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                    .as(expression)
                    .isThrownBy(() -> TargetTopicFilter.validateFnFilter(expression, DittoHeaders.empty()))
                    .withMessageContaining("compared value 'exists'");
        }
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
