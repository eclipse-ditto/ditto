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

    // ===== matchesFnFilter(): match / non-match against a signal =====

    @Test
    public void matchesFnFilterEqMatchesOnDittoOriginator() {
        final Signal<?> signal = thingModifiedWithHeader("ditto-originator", "some:subject");

        assertThat(TargetTopicFilter.matchesFnFilter(
                "fn:filter(header:ditto-originator,'eq','some:subject')", signal, CONNECTION_ID)).isTrue();
        assertThat(TargetTopicFilter.matchesFnFilter(
                "fn:filter(header:ditto-originator,'eq','other:subject')", signal, CONNECTION_ID)).isFalse();
    }

    @Test
    public void matchesFnFilterNeMatchesOnDittoOriginator() {
        final Signal<?> signal = thingModifiedWithHeader("ditto-originator", "some:subject");

        assertThat(TargetTopicFilter.matchesFnFilter(
                "fn:filter(header:ditto-originator,'ne','other:subject')", signal, CONNECTION_ID)).isTrue();
        assertThat(TargetTopicFilter.matchesFnFilter(
                "fn:filter(header:ditto-originator,'ne','some:subject')", signal, CONNECTION_ID)).isFalse();
    }

    @Test
    public void matchesFnFilterOnDittoOriginHeader() {
        final Signal<?> signal = thingModifiedWithHeader("ditto-origin", "some-origin");

        assertThat(TargetTopicFilter.matchesFnFilter(
                "fn:filter(header:ditto-origin,'eq','some-origin')", signal, CONNECTION_ID)).isTrue();
        assertThat(TargetTopicFilter.matchesFnFilter(
                "fn:filter(header:ditto-origin,'eq','other-origin')", signal, CONNECTION_ID)).isFalse();
    }

    // ===== matchesFnFilter(): absent-header semantics (verified facts, Fact 5) =====

    @Test
    public void matchesFnFilterAbsentHeaderEqDrops() {
        final Signal<?> signal = thingModifiedWithHeaders(Collections.emptyMap());

        assertThat(TargetTopicFilter.matchesFnFilter(
                "fn:filter(header:ditto-originator,'eq','x')", signal, CONNECTION_ID)).isFalse();
    }

    @Test
    public void matchesFnFilterAbsentHeaderNePublishes() {
        final Signal<?> signal = thingModifiedWithHeaders(Collections.emptyMap());

        assertThat(TargetTopicFilter.matchesFnFilter(
                "fn:filter(header:ditto-originator,'ne','x')", signal, CONNECTION_ID)).isTrue();
    }

    @Test
    public void matchesFnFilterAbsentHeaderLikeDrops() {
        final Signal<?> signal = thingModifiedWithHeaders(Collections.emptyMap());

        assertThat(TargetTopicFilter.matchesFnFilter(
                "fn:filter(header:ditto-originator,'like','some:*')", signal, CONNECTION_ID)).isFalse();
    }

    @Test
    public void matchesFnFilterAbsentHeaderTwoParamExistsDrops() {
        final Signal<?> signal = thingModifiedWithHeaders(Collections.emptyMap());

        assertThat(TargetTopicFilter.matchesFnFilter(
                "fn:filter(header:ditto-originator,'exists')", signal, CONNECTION_ID)).isFalse();
    }

    // ===== matchesFnFilter(): chained stages (AND semantics) =====

    @Test
    public void matchesFnFilterChainedStagesBothMatchPublishes() {
        final Signal<?> signal = thingModifiedWithHeaders(Map.of(
                "ditto-originator", "some:subject",
                "ditto-origin", "some-origin"));

        assertThat(TargetTopicFilter.matchesFnFilter(
                "fn:filter(header:ditto-originator,'eq','some:subject')" +
                        "|fn:filter(header:ditto-origin,'eq','some-origin')", signal, CONNECTION_ID)).isTrue();
    }

    @Test
    public void matchesFnFilterChainedStagesFirstNonMatchDrops() {
        final Signal<?> signal = thingModifiedWithHeaders(Map.of(
                "ditto-originator", "other:subject",
                "ditto-origin", "some-origin"));

        assertThat(TargetTopicFilter.matchesFnFilter(
                "fn:filter(header:ditto-originator,'eq','some:subject')" +
                        "|fn:filter(header:ditto-origin,'eq','some-origin')", signal, CONNECTION_ID)).isFalse();
    }

    @Test
    public void matchesFnFilterChainedStagesSecondNonMatchDrops() {
        final Signal<?> signal = thingModifiedWithHeaders(Map.of(
                "ditto-originator", "some:subject",
                "ditto-origin", "other-origin"));

        assertThat(TargetTopicFilter.matchesFnFilter(
                "fn:filter(header:ditto-originator,'eq','some:subject')" +
                        "|fn:filter(header:ditto-origin,'eq','some-origin')", signal, CONNECTION_ID)).isFalse();
    }

    // ===== matchesFnFilter(): placeholder-first form (no seed) =====

    @Test
    public void matchesFnFilterPlaceholderFirstNeMatchesOnDittoOriginator() {
        final Signal<?> signal = thingModifiedWithHeader("ditto-originator", "some:subject");

        assertThat(TargetTopicFilter.matchesFnFilter(
                "header:ditto-originator|fn:filter('ne','other:subject')", signal, CONNECTION_ID)).isTrue();
        assertThat(TargetTopicFilter.matchesFnFilter(
                "header:ditto-originator|fn:filter('ne','some:subject')", signal, CONNECTION_ID)).isFalse();
    }

    @Test
    public void matchesFnFilterPlaceholderFirstEqMatchesOnDittoOriginator() {
        final Signal<?> signal = thingModifiedWithHeader("ditto-originator", "some:subject");

        assertThat(TargetTopicFilter.matchesFnFilter(
                "header:ditto-originator|fn:filter('eq','some:subject')", signal, CONNECTION_ID)).isTrue();
        assertThat(TargetTopicFilter.matchesFnFilter(
                "header:ditto-originator|fn:filter('eq','other:subject')", signal, CONNECTION_ID)).isFalse();
    }

    @Test
    public void matchesFnFilterPlaceholderFirstAbsentHeaderAlwaysDrops() {
        // an absent leading placeholder never resolves, so a following fn:filter never matches - unlike the
        // function-first fn:filter(header:x,'ne',...) form, which publishes on an absent header
        // (matchesFnFilterAbsentHeaderNePublishes)
        final Signal<?> signal = thingModifiedWithHeaders(Collections.emptyMap());

        assertThat(TargetTopicFilter.matchesFnFilter(
                "header:ditto-originator|fn:filter('ne','x')", signal, CONNECTION_ID)).isFalse();
        assertThat(TargetTopicFilter.matchesFnFilter(
                "header:ditto-originator|fn:filter('eq','x')", signal, CONNECTION_ID)).isFalse();
    }

    @Test
    public void matchesFnFilterPlaceholderFirstAbsentHeaderWithInterveningDefaultPublishes() {
        // later stages DO run on the unresolved element: an intervening fn:default supplies a value, so the
        // following fn:filter can match again - locks the documented "only an intervening fn:default('...')
        // could supply a value" caveat of the absent-header rule
        final Signal<?> signal = thingModifiedWithHeaders(Collections.emptyMap());

        assertThat(TargetTopicFilter.matchesFnFilter(
                "header:ditto-originator|fn:default('y')|fn:filter('ne','x')", signal, CONNECTION_ID)).isTrue();
    }

    @Test
    public void matchesFnFilterPlaceholderFirstWithTopicPlaceholder() {
        // ThingModified -> topic:action resolves to "modified" (Resolvers.forSignal derives the topic path itself)
        final Signal<?> signal = thingModifiedWithHeaders(Collections.emptyMap());

        assertThat(TargetTopicFilter.matchesFnFilter(
                "topic:action|fn:filter('eq','modified')", signal, CONNECTION_ID)).isTrue();
        assertThat(TargetTopicFilter.matchesFnFilter(
                "topic:action|fn:filter('eq','created')", signal, CONNECTION_ID)).isFalse();
    }

    @Test
    public void matchesFnFilterPlaceholderFirstWithThingJsonPlaceholderOnEvent() {
        // thing-json can only be used as the LEADING stage (the function-parameter grammar \w+: excludes the dash);
        // for a ThingEvent, Resolvers.forSignal feeds it the event-derived thing (attribute test=42 in the helper)
        final Signal<?> signal = thingModifiedWithHeaders(Collections.emptyMap());

        assertThat(TargetTopicFilter.matchesFnFilter(
                "thing-json:attributes/test|fn:filter('eq','42')", signal, CONNECTION_ID)).isTrue();
        assertThat(TargetTopicFilter.matchesFnFilter(
                "thing-json:attributes/test|fn:filter('eq','43')", signal, CONNECTION_ID)).isFalse();
    }

    @Test
    public void matchesFnFilterBarePlaceholderPublishesIffItResolves() {
        // grammatically valid but degenerate (no fn:filter stage): documented as "publish iff the placeholder resolves"
        assertThat(TargetTopicFilter.matchesFnFilter("header:ditto-originator",
                thingModifiedWithHeader("ditto-originator", "some:subject"), CONNECTION_ID)).isTrue();
        assertThat(TargetTopicFilter.matchesFnFilter("header:ditto-originator",
                thingModifiedWithHeaders(Collections.emptyMap()), CONNECTION_ID)).isFalse();
    }

    @Test
    public void matchesFnFilterTrimsSurroundingWhitespace() {
        final Signal<?> signal = thingModifiedWithHeader("ditto-originator", "some:subject");

        assertThat(TargetTopicFilter.matchesFnFilter(
                "  fn:filter(header:ditto-originator,'eq','some:subject')  ", signal, CONNECTION_ID)).isTrue();
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

    // ===== matchesFnFilter(): the documented "last stage must be fn:filter" facts, locked =====

    @Test
    public void matchesFnFilterTrailingDefaultStageAlwaysPublishes() {
        final Signal<?> signal = thingModifiedWithHeader("ditto-originator", "some:subject");

        // the fn:filter stage does NOT match, yet the trailing fn:default resolves the pipeline again
        assertThat(TargetTopicFilter.matchesFnFilter(
                "fn:filter(header:ditto-originator,'eq','other:subject')|fn:default('x')", signal, CONNECTION_ID))
                .isTrue();
    }

    @Test
    public void matchesFnFilterTrailingDeleteStageAlwaysSuppresses() {
        final Signal<?> signal = thingModifiedWithHeader("ditto-originator", "some:subject");

        // the fn:filter stage matches, yet the trailing fn:delete yields a deleted (non-resolved) element
        assertThat(TargetTopicFilter.matchesFnFilter(
                "fn:filter(header:ditto-originator,'eq','some:subject')|fn:delete()", signal, CONNECTION_ID))
                .isFalse();
    }

    @Test
    public void matchesFnFilterTrailingValueProducingStageLeavesDecisionUnchanged() {
        final Signal<?> signal = thingModifiedWithHeader("ditto-originator", "some:subject");

        assertThat(TargetTopicFilter.matchesFnFilter(
                "fn:filter(header:ditto-originator,'eq','some:subject')|fn:upper()", signal, CONNECTION_ID)).isTrue();
        assertThat(TargetTopicFilter.matchesFnFilter(
                "fn:filter(header:ditto-originator,'eq','other:subject')|fn:upper()", signal, CONNECTION_ID)).isFalse();
    }

    // ===== validateFnFilter(): invalid expressions =====

    @Test
    public void validateFnFilterAcceptsSingleStage() {
        assertThatNoException().isThrownBy(() ->
                TargetTopicFilter.validateFnFilter("fn:filter(header:ditto-originator,'ne','x')",
                        DittoHeaders.empty()));
    }

    @Test
    public void validateFnFilterRejectsUnknownFunction() {
        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class).isThrownBy(() ->
                TargetTopicFilter.validateFnFilter("fn:unknownfn('x')", DittoHeaders.empty()));
    }

    @Test
    public void validateFnFilterRejectsFilterWithoutArguments() {
        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class).isThrownBy(() ->
                TargetTopicFilter.validateFnFilter("fn:filter()", DittoHeaders.empty()));
    }

    @Test
    public void validateFnFilterRejectsUnknownPlaceholderPrefix() {
        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class).isThrownBy(() ->
                TargetTopicFilter.validateFnFilter("fn:filter(bogus:x,'eq','y')", DittoHeaders.empty()));
    }

    // ===== validateFnFilter(): chained stages, pipeline grammar limits =====

    @Test
    public void validateFnFilterAcceptsChainedStages() {
        assertThatNoException().isThrownBy(() ->
                TargetTopicFilter.validateFnFilter(
                        "fn:filter(header:a,'exists')|fn:filter(header:b,'exists')", DittoHeaders.empty()));
    }

    @Test
    public void validateFnFilterRejectsChainedParamWithUnknownFunctionStage() {
        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class).isThrownBy(() ->
                TargetTopicFilter.validateFnFilter(
                        "fn:filter(header:a,'exists')|fn:unknownfn('x')", DittoHeaders.empty()));
    }

    @Test
    public void validateFnFilterAcceptsTenChainedStagesButRejectsEleven() {
        // the resolver caps a pipeline at 11 stages in total; the internal seed occupies one slot, leaving 10 user
        // fn: stages
        final String tenStages = String.join("|", Collections.nCopies(10, "fn:filter(header:a,'exists')"));
        assertThatNoException().isThrownBy(() ->
                TargetTopicFilter.validateFnFilter(tenStages, DittoHeaders.empty()));

        final String elevenStages = String.join("|", Collections.nCopies(11, "fn:filter(header:a,'exists')"));
        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class).isThrownBy(() ->
                TargetTopicFilter.validateFnFilter(elevenStages, DittoHeaders.empty()));
    }

    @Test
    public void validateFnFilterAcceptsQuotedPipeInsideChainedStages() {
        // the resolver's stage split is quote-aware: the '|' inside 'a|b' must not be taken for a stage separator
        assertThatNoException().isThrownBy(() ->
                TargetTopicFilter.validateFnFilter(
                        "fn:filter(header:a,'eq','a|b')|fn:filter(header:b,'exists')", DittoHeaders.empty()));
    }

    @Test
    public void validateFnFilterRejectsTrailingPipe() {
        // rejected by the resolver's pipeline grammar (empty trailing stage), no custom scan involved
        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class).isThrownBy(() ->
                TargetTopicFilter.validateFnFilter("fn:filter(header:a,'exists')|", DittoHeaders.empty()));
    }

    @Test
    public void validateFnFilterAcceptsQuotedPipeInSingleQuotedConstant() {
        assertThatNoException().isThrownBy(() ->
                TargetTopicFilter.validateFnFilter("fn:filter(header:a,'eq','a|b')", DittoHeaders.empty()));
    }

    @Test
    public void validateFnFilterAcceptsQuotedPipeInDoubleQuotedConstant() {
        assertThatNoException().isThrownBy(() ->
                TargetTopicFilter.validateFnFilter("fn:filter(header:a,'eq',\"a|b\")", DittoHeaders.empty()));
    }

    @Test
    public void validateFnFilterTrailingBackslashDoesNotThrowUnexpectedly() {
        // a trailing backslash must never escape the documented exception contract; the resolver validation may
        // still reject the expression, but only ever with the documented exception type
        final Throwable throwable = catchThrowable(() ->
                TargetTopicFilter.validateFnFilter("fn:filter(header:a,'exists')\\", DittoHeaders.empty()));

        if (throwable != null) {
            assertThat(throwable).isInstanceOf(ConnectionConfigurationInvalidException.class);
        }
    }

    // ===== validateFnFilter(): placeholder-first form, misplaced RQL, nameless placeholder =====

    @Test
    public void validateFnFilterAcceptsPlaceholderFirstPipeline() {
        // if the seed were (wrongly) prepended, "header:ditto-originator" would become a second stage and be
        // rejected as an unknown function
        assertThatNoException().isThrownBy(() -> TargetTopicFilter.validateFnFilter(
                "header:ditto-originator|fn:filter('ne','x')", DittoHeaders.empty()));
        assertThatNoException().isThrownBy(() -> TargetTopicFilter.validateFnFilter(
                "thing-json:attributes/test|fn:filter('eq','42')", DittoHeaders.empty()));
    }

    @Test
    public void validateFnFilterRejectsUnknownLeadingPlaceholderPrefix() {
        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class).isThrownBy(() ->
                TargetTopicFilter.validateFnFilter("bogus:x|fn:filter('eq','y')", DittoHeaders.empty()));
    }

    @Test
    public void validateFnFilterRejectsThingJsonAsFunctionParameter() {
        // pre-existing library restriction: a function parameter placeholder prefix must match \w+ (no dash), so
        // thing-json is only usable as the leading stage of a placeholder-first pipeline (documented, D18)
        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class).isThrownBy(() ->
                TargetTopicFilter.validateFnFilter("fn:filter(thing-json:attributes/test,'eq','42')",
                        DittoHeaders.empty()));
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

    @Test
    public void validateFnFilterPlaceholderFirstAcceptsTenFunctionStagesButRejectsEleven() {
        // the leading placeholder occupies the grammar's leading slot instead of the internal seed, so the user
        // budget is 10 fn: stages in both forms (the resolver caps a pipeline at 11 elements in total)
        final String tenStages = "header:a|" + String.join("|", Collections.nCopies(10, "fn:filter('ne','zzz')"));
        assertThatNoException().isThrownBy(() ->
                TargetTopicFilter.validateFnFilter(tenStages, DittoHeaders.empty()));

        final String elevenStages = "header:a|" + String.join("|", Collections.nCopies(11, "fn:filter('ne','zzz')"));
        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class).isThrownBy(() ->
                TargetTopicFilter.validateFnFilter(elevenStages, DittoHeaders.empty()));
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
