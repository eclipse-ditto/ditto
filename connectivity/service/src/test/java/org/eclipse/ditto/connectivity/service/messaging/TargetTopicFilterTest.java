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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.connectivity.model.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
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

    /**
     * The closing sentence of a rejection message which suggests a complete replacement expression (group 1).
     */
    private static final Pattern SUGGESTED_REWRITE =
            Pattern.compile(" (?:Write|To publish when \\S+ does not resolve write) (.+) instead\\.$");

    /**
     * The closing part of a rejection message which suggests a replacement (group 1) for the stage it rejects.
     */
    private static final Pattern SUGGESTED_STAGE = Pattern.compile(", here '(.+)'\\.$");

    /**
     * The stage which a rejection message names as the rejected one (group 1).
     */
    private static final Pattern REJECTED_STAGE =
            Pattern.compile("The (?:stage|last stage|leading placeholder) '(.+?)' (?=[a-z])");

    /**
     * A placeholder-first pipeline expression quoted as an example in a rejection message or description, either
     * concrete ({@code header:x|fn:...}) or as a template ({@code <placeholder>|fn:...}).
     */
    private static final Pattern EXAMPLE_EXPRESSION = Pattern.compile(
            "(?:<placeholder>|[\\w-]+:[\\w/-]+)(?:\\|fn:[\\w-]+\\((?:[^()']|'[^']*')*\\))+");

    /**
     * A complete topic query string quoted as an example in a rejection message.
     */
    private static final Pattern EXAMPLE_TOPIC_QUERY = Pattern.compile("\\?fn-filter=\\S+\\)");

    // ===== isFunctionExpression() / isPipelineExpression(): classification of a raw parameter value =====

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
        // RQL expressions - also with an unquoted '|' in a property path, a quoted "|fn:" or a trailing "|fn:..." stage
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
    public void matchesFnFilterExistsTrueMatchesPresentAndDropsAbsentHeader() {
        final String fnFilter = "header:ditto-originator|fn:filter('exists','true')";

        assertThat(TargetTopicFilter.matchesFnFilter(fnFilter,
                thingModifiedWithHeader("ditto-originator", "some:subject"), CONNECTION_ID)).isTrue();
        assertThat(TargetTopicFilter.matchesFnFilter(fnFilter,
                thingModifiedWithHeaders(Collections.emptyMap()), CONNECTION_ID)).isFalse();
    }

    @Test
    public void matchesFnFilterExistsTestsTheResolvedValueForBeingNonEmpty() {
        // a present but empty header resolves to the empty value: 'exists','true' suppresses it, and it is the only
        // value 'exists','false' could match - an absent header never reaches the stage, which is why
        // validateFnFilter rejects 'exists','false' and points to fn:default for publishing on an absent header
        final Signal<?> emptyHeader = thingModifiedWithHeader("x", "");
        final Signal<?> blankHeader = thingModifiedWithHeader("x", " ");
        final Signal<?> absentHeader = thingModifiedWithHeaders(Collections.emptyMap());
        final Signal<?> presentHeader = thingModifiedWithHeader("x", "v");

        final String existsTrue = "header:x|fn:filter('exists','true')";
        assertThat(TargetTopicFilter.matchesFnFilter(existsTrue, emptyHeader, CONNECTION_ID)).isFalse();
        assertThat(TargetTopicFilter.matchesFnFilter(existsTrue, blankHeader, CONNECTION_ID)).isTrue();
        assertThat(TargetTopicFilter.matchesFnFilter(existsTrue, presentHeader, CONNECTION_ID)).isTrue();

        final String existsFalse = "header:x|fn:filter('exists','false')";
        assertThat(TargetTopicFilter.matchesFnFilter(existsFalse, emptyHeader, CONNECTION_ID)).isTrue();
        assertThat(TargetTopicFilter.matchesFnFilter(existsFalse, absentHeader, CONNECTION_ID)).isFalse();
        assertThat(TargetTopicFilter.matchesFnFilter(existsFalse, presentHeader, CONNECTION_ID)).isFalse();

        final String publishWhenUnresolved = "header:x|fn:default('<none>')|fn:filter('eq','<none>')";
        assertThat(TargetTopicFilter.matchesFnFilter(publishWhenUnresolved, absentHeader, CONNECTION_ID)).isTrue();
        assertThat(TargetTopicFilter.matchesFnFilter(publishWhenUnresolved, presentHeader, CONNECTION_ID)).isFalse();
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
        // a compared placeholder which does not resolve drops the signal for every rqlFunction, also for 'ne'
        final Signal<?> signal = thingModifiedWithHeader("ditto-originator", "some:subject");

        assertThat(TargetTopicFilter.matchesFnFilter(
                "header:ditto-originator|fn:filter('ne',header:absent)", signal, CONNECTION_ID)).isFalse();
        assertThat(TargetTopicFilter.matchesFnFilter(
                "header:ditto-originator|fn:filter('eq',header:absent)", signal, CONNECTION_ID)).isFalse();
    }

    @Test
    public void matchesFnFilterUnknownRqlFunctionNameNeverMatches() {
        // fn:filter never matches for an unknown rqlFunction name, which is why validateFnFilter rejects such literals
        final Signal<?> signal = thingModifiedWithHeader("ditto-originator", "some:subject");

        assertThat(TargetTopicFilter.matchesFnFilter(
                "header:ditto-originator|fn:filter('nope','some:subject')", signal, CONNECTION_ID)).isFalse();
        assertThat(TargetTopicFilter.matchesFnFilter(
                "header:ditto-originator|fn:filter('nope','other:subject')", signal, CONNECTION_ID)).isFalse();
    }

    @Test
    public void matchesFnFilterStageWithoutFnPrefixNeverMatches() {
        // the function lookup accepts a name without the "fn:" prefix, but no function is applied and the stage
        // yields an unresolved value, which is why validateFnFilter rejects such a stage
        final Signal<?> signal = thingModifiedWithHeader("ditto-originator", "Some:Subject");

        assertThat(TargetTopicFilter.matchesFnFilter(
                "header:ditto-originator|lower()|fn:filter('eq','some:subject')", signal, CONNECTION_ID)).isFalse();
        assertThat(TargetTopicFilter.matchesFnFilter(
                "header:ditto-originator|fn:lower()|filter('eq','some:subject')", signal, CONNECTION_ID)).isFalse();
    }

    @Test
    public void matchesFnFilterExistsWithComparedPlaceholderResolvingToFalseNeverMatches() {
        // a compared placeholder resolving to "false" makes the stage fn:filter('exists','false'), which is why
        // validateFnFilter rejects a placeholder as compared value of 'exists'
        final String fnFilter = "header:ditto-originator|fn:filter('exists',header:flag)";

        assertThat(TargetTopicFilter.matchesFnFilter(fnFilter,
                thingModifiedWithHeaders(Map.of("ditto-originator", "some:subject", "flag", "false")),
                CONNECTION_ID)).isFalse();
        assertThat(TargetTopicFilter.matchesFnFilter(fnFilter,
                thingModifiedWithHeader("flag", "false"), CONNECTION_ID)).isFalse();
    }

    @Test
    public void matchesFnFilterLiteralComparedValueExistsIsTakenAsTheExistsForm() {
        // fn:filter('ne','exists') is parsed as fn:filter(<filterValue>,'exists') with the constant 'ne' as filter
        // value: it matches every resolved pipeline value, even 'exists' itself - which is why validateFnFilter
        // rejects the literal compared value 'exists'
        assertThat(TargetTopicFilter.matchesFnFilter("header:ditto-originator|fn:filter('ne','exists')",
                thingModifiedWithHeader("ditto-originator", "exists"), CONNECTION_ID)).isTrue();
    }

    @Test
    public void matchesFnFilterLeadingFilterFunctionNeverMatches() {
        // an expression starting with a function is rejected by validateFnFilter; evaluated anyway, a leading
        // fn:filter never matches, as there is no leading placeholder whose value it could keep
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
        // "header:" passes the grammar (the headers placeholder supports any name) but resolving its value throws an
        // IllegalArgumentException - hence the name check in validateFnFilter and the callers catching
        // RuntimeException
        final Signal<?> signal = thingModifiedWithHeader("ditto-originator", "some:subject");

        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                TargetTopicFilter.matchesFnFilter("header:|fn:filter('eq','x')", signal, CONNECTION_ID));
    }

    @Test
    public void matchesFnFilterLeadingHeaderWithWhitespaceNextToItsColonNeverMatches() {
        // whitespace next to the colon is not dropped when the header is looked up, so the header is never found -
        // which is why validateFnFilter rejects such a leading placeholder
        final Signal<?> signal = thingModifiedWithHeader("x", "other");

        assertThat(TargetTopicFilter.matchesFnFilter("header:x|fn:filter('ne','v')", signal, CONNECTION_ID)).isTrue();
        for (final String fnFilter : List.of(
                "header :x|fn:filter('ne','v')",
                "header: x|fn:filter('ne','v')",
                "header : x|fn:filter('ne','v')")) {
            assertThat(TargetTopicFilter.matchesFnFilter(fnFilter, signal, CONNECTION_ID)).as(fnFilter).isFalse();
        }
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
                "header:x|fn:default('a|fn:filter(b)')|fn:filter('ne','y')",
                "header:x|fn:filter('eq','a|lower()')",
                "header:x|fn:default('a|lower()')|fn:filter('ne','y')",
                "header:x|fn:default(\"a|lower()|b\")|fn:filter('ne','y')")) {
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
        // the resolver's pipeline grammar rejects the empty trailing stage
        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class).isThrownBy(() ->
                TargetTopicFilter.validateFnFilter("header:a|fn:filter('ne','x')|", DittoHeaders.empty()));
    }

    @Test
    public void validateFnFilterRejectsTrailingBackslash() {
        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class).isThrownBy(() ->
                TargetTopicFilter.validateFnFilter("header:a|fn:filter('ne','x')\\", DittoHeaders.empty()));
    }

    @Test
    public void validateFnFilterRejectsRqlExpressionWithHintToFilterParameter() {
        final Throwable thrown = catchThrowable(() ->
                TargetTopicFilter.validateFnFilter("eq(attributes/x,1)", DittoHeaders.empty()));

        assertThat(thrown).isInstanceOf(ConnectionConfigurationInvalidException.class)
                .hasMessageContaining("'fn-filter'")
                .hasMessageContaining("The placeholder 'eq(attributes/x,1)' could not be resolved.");
        assertThat(((DittoRuntimeException) thrown).getDescription()).hasValueSatisfying(description ->
                assertThat(description).contains("RQL expressions belong in the 'filter' parameter"));
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
        // "header:" and "thing-json:" accept any name at grammar level and validation never resolves placeholder
        // values: without the name check "header:" would throw per signal at runtime and "header :" (parsed with the
        // name ":") would never match
        for (final String expression : List.of("header:|fn:filter('eq','x')", "header:", "  header: |fn:upper()",
                "header :|fn:filter('eq','x')", "thing-json:|fn:filter('ne','x')")) {
            assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                    .isThrownBy(() -> TargetTopicFilter.validateFnFilter(expression, DittoHeaders.empty()))
                    .withMessageContaining("has no name");
        }
    }

    @Test
    public void validateFnFilterRejectsLeadingPlaceholderWithWhitespaceNextToItsColon() {
        // "header" and "thing-json" accept any name at grammar level, so the grammar lets the whitespace pass (see
        // matchesFnFilterLeadingHeaderWithWhitespaceNextToItsColonNeverMatches)
        final Map<String, String> expectedLeadingPlaceholders = Map.of(
                "header :x|fn:filter('ne','v')", "header:x",
                "header: x|fn:filter('ne','v')", "header:x",
                "header : x|fn:filter('ne','v')", "header:x",
                "  header:\tditto-originator | fn:lower() | fn:filter('ne','v')  ", "header:ditto-originator",
                "thing-json: attributes/x|fn:filter('eq','v')", "thing-json:attributes/x",
                "thing-json :attributes/x|fn:filter('eq','v')", "thing-json:attributes/x");

        expectedLeadingPlaceholders.forEach((expression, expectedLeadingPlaceholder) ->
                assertThat(rejectionOf(expression).getMessage())
                        .as(expression)
                        .contains("The leading placeholder '")
                        .contains("whitespace next to its ':'")
                        .endsWith(", here '" + expectedLeadingPlaceholder + "'."));
        // placeholders with a fixed set of names are rejected by the grammar already
        for (final String expression : List.of(
                "topic: action|fn:filter('ne','deleted')",
                "topic :action|fn:filter('ne','deleted')",
                "time: now|fn:filter('exists','true')")) {
            assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                    .as(expression)
                    .isThrownBy(() -> TargetTopicFilter.validateFnFilter(expression, DittoHeaders.empty()));
        }
    }

    @Test
    public void validateFnFilterAcceptsWhitespaceAroundTheExpressionAndAroundItsStages() {
        for (final String expression : List.of(
                "header:x|fn:filter('ne','v')",
                "time:now|fn:filter('exists','true')",
                "thing-json:attributes/x|fn:filter('eq','v')",
                "topic:action|fn:filter('ne','deleted')",
                "  header:x|fn:filter('ne','v')  ",
                "\theader:x|fn:filter('ne','v')\n",
                "header:x | fn:lower() | fn:filter('ne','v')",
                " thing-json:attributes/x | fn:filter( 'eq' , 'v' ) ",
                // only whitespace next to the colon of the leading placeholder counts
                "header:x|fn:filter('ne','a : b')",
                "header:x|fn:default('a: b')|fn:filter('ne','v')")) {
            assertThatNoException()
                    .as(expression)
                    .isThrownBy(() -> TargetTopicFilter.validateFnFilter(expression, DittoHeaders.empty()));
        }
    }

    // ===== validateFnFilter(): the pipeline shape - placeholder first, exactly one fn:filter, as the last stage =====

    @Test
    public void validateFnFilterRejectsFunctionFirstExpression() {
        // the filtered value must be the leading placeholder, which drops the signal when it does not resolve; an
        // expression starting with a function has no value to filter
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
                    .withMessageContaining("must start with a placeholder")
                    .withMessageContaining("without a leading placeholder the pipeline has no value to filter");
        }
    }

    @Test
    public void validateFnFilterExplainsTheLeadingFilterStageOnlyForExpressionsStartingWithIt() {
        // only a leading fn:filter never resolves (see matchesFnFilterLeadingFilterFunctionNeverMatches)
        for (final String expression : List.of(
                "fn:filter(header:ditto-originator,'ne','x')",
                " fn:filter(header:ditto-originator,'exists')",
                "fn:filter('ne','x')")) {
            assertThat(rejectionOf(expression).getMessage())
                    .as(expression)
                    .contains("A leading fn:filter(...) stage never resolves");
        }
        for (final String expression : List.of(
                "fn:default(header:a)|fn:filter('ne','x')",
                "fn:upper()",
                "fn:delete()")) {
            assertThat(rejectionOf(expression).getMessage())
                    .as(expression)
                    .doesNotContain("A leading fn:filter(...)");
        }
    }

    @Test
    public void validateFnFilterSuggestsThePlaceholderFirstRewriteOfASimpleFunctionFirstFilter() {
        final Map<String, String> expectedRewrites = Map.of(
                "fn:filter(header:ditto-originator, 'ne', 'some:subject')",
                "header:ditto-originator|fn:filter('ne','some:subject')",
                "fn:filter(header:ditto-originator,'exists')",
                "header:ditto-originator|fn:filter('exists','true')",
                "fn:filter(header:ditto-originator,\"exists\")",
                "header:ditto-originator|fn:filter(\"exists\",'true')",
                "fn:filter(header:ditto-originator,'exists','true')",
                "header:ditto-originator|fn:filter('exists','true')",
                "fn:filter(header:x,'like','a|b,c')",
                "header:x|fn:filter('like','a|b,c')",
                "fn:filter(thing-json:attributes/a,'eq',header:expected)",
                "thing-json:attributes/a|fn:filter('eq',header:expected)");

        expectedRewrites.forEach((expression, expectedRewrite) -> {
            assertThat(suggestedRewriteOf(expression)).as(expression).contains(expectedRewrite);
            assertSuggestedRewriteIsAbsentOrValid(expression);
        });
    }

    @Test
    public void validateFnFilterSuggestsTheDefaultRecipeForAFunctionFirstExistsFalseFilter() {
        // <placeholder>|fn:filter('exists','false') is rejected itself, so the rewrite is the recipe its rejection
        // message gives
        for (final String expression : List.of(
                "fn:filter(header:x,'exists','false')",
                "fn:filter(header:x, 'exists', \"FALSE\")",
                "fn:filter(header:x,'exists','yes')")) {
            assertThat(suggestedRewriteOf(expression))
                    .as(expression)
                    .contains("header:x|fn:default('<none>')|fn:filter('eq','<none>')");
            assertSuggestedRewriteIsAbsentOrValid(expression);
            assertThat(rejectionOf(expression).getMessage())
                    .as(expression)
                    .contains("To publish when header:x does not resolve write header:x|fn:default(");
        }
    }

    @Test
    public void validateFnFilterSuggestsNoRewriteWhichIsRejectedOrInventsAComparedValue() {
        for (final String expression : List.of(
                // the rewrite would be rejected: unknown rqlFunction, compared value 'exists', placeholder without
                // a name, unknown placeholder, unquoted compared value, compared placeholder for 'exists'
                "fn:filter(header:ditto-originator,'NE','excluded:subject')",
                "fn:filter(header:ditto-originator,'neq','excluded:subject')",
                "fn:filter(header:x,'ne','exists')",
                "fn:filter(header:,'ne','v')",
                "fn:filter(header:,'exists','false')",
                "fn:filter(bogus:x,'ne','v')",
                "fn:filter(header:qos,'ne',0)",
                "fn:filter(header:x,'exists',header:flag)",
                // no compared value is given: none is made up
                "fn:filter(header:ditto-originator,'ne')",
                "fn:filter(header:ditto-originator,'eq')",
                // not one of the simple forms
                "fn:filter(header:x,'ne','a','b')",
                "fn:filter(header:x,'exists','false','b')",
                "fn:filter('a','ne','b')",
                "fn:filter(header:x,header:op,'v')",
                "fn:filter(fn:upper(),'ne','v')",
                "fn:filter('ne','x')",
                "fn:filter(header:a,'eq','x')|fn:filter(header:b,'eq','y')",
                "fn:filter(header:a,'eq','x')|fn:default('y')",
                "fn:default(header:a)|fn:filter('ne','x')",
                "fn:upper()")) {
            assertThat(suggestedRewriteOf(expression)).as(expression).isEmpty();
        }
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
        // a second fn:filter can only test another value by taking a placeholder as its filter value, which is
        // filtered as the empty value when absent; several conditions are ANDed by repeating the fn-filter parameter
        for (final String expression : List.of(
                "header:a|fn:filter('ne','x')|fn:filter('ne','y')",
                "header:a|fn:filter('ne','x')|fn:filter(header:b,'ne','y')",
                "header:a|fn:filter('ne','x')|fn:default('y')|fn:filter('exists','true')",
                "header:a|fn:filter('exists','true')|fn:lower()|fn:filter('eq','x')")) {
            assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                    .as(expression)
                    .isThrownBy(() -> TargetTopicFilter.validateFnFilter(expression, DittoHeaders.empty()))
                    .withMessageContaining("only one fn:filter")
                    .withMessageContaining("repeat the 'fn-filter' parameter")
                    .withMessageContaining("?fn-filter=header:ditto-originator|fn:filter('ne','some:subject')" +
                            "&fn-filter=topic:action|fn:filter('ne','deleted')");
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
                    .withMessageContaining("no later stage can resolve")
                    .withMessageContaining("remove the stage");
        }
    }

    @Test
    public void validateFnFilterRejectsStageWithoutFnPrefix() {
        // the function lookup of the placeholders library takes the "fn:" prefix as optional, but applies no
        // function without it: such a stage passes the grammar and never resolves (see
        // matchesFnFilterStageWithoutFnPrefixNeverMatches)
        for (final String expression : List.of(
                "header:a|lower()|fn:filter('eq','x')",
                "header:a| lower() |fn:filter('eq','x')",
                "header:a|default('y')|fn:filter('eq','x')",
                "header:a|delete()|fn:filter('eq','x')",
                "header:a|fn:lower()|filter('eq','x')",
                "header:a|filter('eq','x')",
                "header:a|lower()")) {
            assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                    .as(expression)
                    .isThrownBy(() -> TargetTopicFilter.validateFnFilter(expression, DittoHeaders.empty()))
                    .withMessageContaining("lacks the 'fn:' prefix")
                    .withMessageContaining("must be a function call starting with 'fn:'");
        }
        assertThat(rejectionOf("header:a|lower()|fn:filter('eq','x')").getMessage())
                .contains("The stage 'lower()'")
                .contains("'fn:lower()'");
        assertThat(rejectionOf("header:a|fn:lower()|filter('eq','x')").getMessage())
                .contains("The stage 'filter('eq','x')'")
                .contains("'fn:filter('eq','x')'");
        // an fn:delete() stage is rejected itself, so it is not suggested
        assertThat(rejectionOf("header:a|delete()|fn:filter('eq','x')").getMessage())
                .contains("The stage 'delete()'")
                .contains("remove the stage")
                .doesNotContain("'fn:delete()'");
    }

    // ===== validateFnFilter(): the fn:filter stage - fn:filter('<rqlFunction>', <comparedValue>) only =====

    @Test
    public void validateFnFilterRejectsFilterStageTakingItsFilterValueAsParameter() {
        // only the 2-parameter form filtering the pipeline's own value is accepted: a filter value passed as
        // parameter is either a placeholder (filtered as the empty value when it does not resolve) or a constant
        // (never looks at the signal), and a placeholder-valued rqlFunction cannot be told apart from a filter value
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
        // an unknown rqlFunction name never matches at runtime -> the topic would never publish
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
        // the filtered value is the pipeline's own, resolved value, so 'exists','false' could only match an empty
        // value, never an absent one (see matchesFnFilterAbsentHeaderAlwaysDrops); "publish when absent" needs an
        // fn:default
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
    public void validateFnFilterRejectsExistsWithPlaceholderAsComparedValue() {
        // 'exists' with a compared placeholder resolving to anything but "true" behaves like 'exists','false' (see
        // matchesFnFilterExistsWithComparedPlaceholderResolvingToFalseNeverMatches)
        for (final String expression : List.of(
                "header:ditto-originator|fn:filter('exists',header:flag)",
                "header:ditto-originator|fn:filter(\"exists\", topic:action )",
                "header:ditto-originator|fn:default('none')|fn:filter('exists',thing:name)")) {
            assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                    .as(expression)
                    .isThrownBy(() -> TargetTopicFilter.validateFnFilter(expression, DittoHeaders.empty()))
                    .withMessageContaining("the compared value of 'exists' must be the constant 'true'")
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

    // ===== validateFnFilter(): expressions and stages suggested in the rejection texts =====

    @Test
    public void everyExpressionAndStageSuggestedInARejectionTextIsAccepted() {
        final List<String> quotedExpressions = new ArrayList<>();
        final List<String> quotedTopicQueries = new ArrayList<>();
        final List<String> suggestedStages = new ArrayList<>();
        final List<String> expressionsWithTheSuggestedStage = new ArrayList<>();
        for (final String expression : List.of(
                "fn:filter(header:ditto-originator,'ne','some:subject')",
                "fn:filter(header:x,'exists','false')",
                "fn:upper()",
                "eq(attributes/x,1)",
                "header:|fn:filter('eq','x')",
                "header : a|fn:filter('eq','x')",
                "thing-json: attributes/a|fn:filter('eq','x')",
                "header:a|lower()|fn:filter('eq','x')",
                "header:a|default('y')|fn:filter('eq','x')",
                "header:a|delete()|fn:filter('eq','x')",
                "header:a|fn:lower()|filter('eq','x')",
                "header:a|fn:delete()|fn:filter('ne','x')",
                "header:a",
                "header:a|fn:upper()",
                "header:a|fn:filter('ne','x')|fn:filter('ne','y')",
                "header:a|fn:filter(header:b,'ne','x')",
                "header:a|fn:filter('NE','x')",
                "header:a|fn:filter('ne','exists')",
                "header:a|fn:filter('exists','false')",
                "header:a|fn:filter('exists',header:flag)")) {
            final ConnectionConfigurationInvalidException rejection = rejectionOf(expression);
            // the message starts by quoting the rejected expression, which is no example
            final String text = rejection.getMessage().replace("'" + expression + "'", "") + " " +
                    rejection.getDescription().orElseThrow();
            final Matcher exampleExpression = EXAMPLE_EXPRESSION.matcher(text);
            while (exampleExpression.find()) {
                quotedExpressions.add(exampleExpression.group());
            }
            final Matcher exampleTopicQuery = EXAMPLE_TOPIC_QUERY.matcher(text);
            while (exampleTopicQuery.find()) {
                quotedTopicQueries.add(exampleTopicQuery.group());
            }
            final Matcher suggestedStage = SUGGESTED_STAGE.matcher(rejection.getMessage());
            if (suggestedStage.find()) {
                suggestedStages.add(suggestedStage.group(1));
                expressionsWithTheSuggestedStage.add(
                        withRejectedStageReplaced(expression, rejection, suggestedStage.group(1)));
            }
        }

        assertThat(quotedExpressions).contains(
                "header:ditto-originator|fn:filter('ne','some:subject')",
                "header:ditto-originator|fn:filter('exists','true')",
                "<placeholder>|fn:filter('exists','true')",
                "<placeholder>|fn:default('<none>')|fn:filter('eq','<none>')",
                "header:x|fn:default('<none>')|fn:filter('eq','<none>')",
                "topic:action|fn:filter('ne','deleted')");
        // a signal caused via the HTTP API carries ditto-originator, but e.g. no ditto-origin: an example which
        // needs any other header would suppress all of those signals
        final Signal<?> signalCausedViaHttp = thingModifiedWithHeader("ditto-originator", "other:subject");
        for (final String quotedExpression : quotedExpressions) {
            final String fnFilter = quotedExpression.replace("<placeholder>", "header:ditto-originator");
            assertThatNoException()
                    .as(quotedExpression)
                    .isThrownBy(() -> TargetTopicFilter.validateFnFilter(fnFilter, DittoHeaders.empty()));
            if (!quotedExpression.contains("<none>")) {
                assertThat(TargetTopicFilter.matchesFnFilter(fnFilter, signalCausedViaHttp, CONNECTION_ID))
                        .as(quotedExpression)
                        .isTrue();
            }
        }

        // a stage suggested in place of the rejected one must make the expression a valid fn-filter
        for (final String expressionWithTheSuggestedStage : expressionsWithTheSuggestedStage) {
            assertThatNoException()
                    .as(expressionWithTheSuggestedStage)
                    .isThrownBy(() -> TargetTopicFilter.validateFnFilter(expressionWithTheSuggestedStage,
                            DittoHeaders.empty()));
        }
        assertThat(suggestedStages).containsExactlyInAnyOrder("header:a", "thing-json:attributes/a", "fn:lower()",
                "fn:default('y')", "fn:filter('eq','x')");

        assertThat(quotedTopicQueries).containsOnly("?fn-filter=header:ditto-originator|fn:filter('ne'," +
                "'some:subject')&fn-filter=topic:action|fn:filter('ne','deleted')");
        for (final String quotedTopicQuery : quotedTopicQueries) {
            final List<String> fnFilters =
                    ConnectivityModelFactory.newFilteredTopic("_/_/things/twin/events" + quotedTopicQuery)
                            .getFnFilters();
            assertThat(fnFilters).as(quotedTopicQuery).hasSize(2);
            for (final String fnFilter : fnFilters) {
                assertThatNoException()
                        .as(fnFilter)
                        .isThrownBy(() -> TargetTopicFilter.validateFnFilter(fnFilter, DittoHeaders.empty()));
            }
        }
    }

    // ===== test helpers =====

    private static ConnectionConfigurationInvalidException rejectionOf(final String fnFilter) {
        final Throwable thrown =
                catchThrowable(() -> TargetTopicFilter.validateFnFilter(fnFilter, DittoHeaders.empty()));

        assertThat(thrown).as(fnFilter).isInstanceOf(ConnectionConfigurationInvalidException.class);
        return (ConnectionConfigurationInvalidException) thrown;
    }

    private static String withRejectedStageReplaced(final String fnFilter,
            final ConnectionConfigurationInvalidException rejection, final String replacement) {
        final Matcher rejectedStage = REJECTED_STAGE.matcher(rejection.getMessage());
        assertThat(rejectedStage.find()).as(rejection.getMessage()).isTrue();
        final String stage = rejectedStage.group(1);
        final String replaced = fnFilter.startsWith(stage)
                ? replacement + fnFilter.substring(stage.length())
                : fnFilter.replace("|" + stage, "|" + replacement);

        assertThat(replaced).as("stage '%s' of %s", stage, fnFilter).isNotEqualTo(fnFilter);
        return replaced;
    }

    private static Optional<String> suggestedRewriteOf(final String fnFilter) {
        final Matcher suggestedRewrite = SUGGESTED_REWRITE.matcher(rejectionOf(fnFilter).getMessage());
        return suggestedRewrite.find() ? Optional.of(suggestedRewrite.group(1)) : Optional.empty();
    }

    private static void assertSuggestedRewriteIsAbsentOrValid(final String fnFilter) {
        suggestedRewriteOf(fnFilter).ifPresent(suggestedRewrite -> assertThatNoException()
                .as("rewrite suggested for " + fnFilter)
                .isThrownBy(() -> TargetTopicFilter.validateFnFilter(suggestedRewrite, DittoHeaders.empty())));
    }

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
