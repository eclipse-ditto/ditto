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

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.connectivity.model.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.service.messaging.TargetTopicFilter.PartitionedFilters;
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

    // ===== isPipelineFilter(): classification =====

    @Test
    public void isPipelineFilterClassifiesByFnPrefix() {
        assertThat(TargetTopicFilter.isPipelineFilter("fn:filter(header:a,'exists')")).isTrue();
        assertThat(TargetTopicFilter.isPipelineFilter(" fn:filter(header:a,'exists')")).isTrue();
        assertThat(TargetTopicFilter.isPipelineFilter("gt(attributes/x,5)")).isFalse();
        // an "fn:" substring anywhere but the (trimmed) start does not make a pipeline filter
        assertThat(TargetTopicFilter.isPipelineFilter("like(attributes/a,'*|fn:x*')")).isFalse();
        assertThat(TargetTopicFilter.isPipelineFilter("")).isFalse();
    }

    // ===== partition(): pure forms =====

    @Test
    public void partitionPurePipelineFilter() {
        final PartitionedFilters partitioned =
                TargetTopicFilter.partition(List.of("fn:filter(header:ditto-originator,'eq','x')"));

        assertThat(partitioned.getRqlExpressions()).isEmpty();
        assertThat(partitioned.hasRqlExpression()).isFalse();
        assertThat(partitioned.getPipelineExpressions())
                .containsExactly("fn:filter(header:ditto-originator,'eq','x')");
    }

    @Test
    public void partitionPureRqlFilters() {
        assertPureRql("and(eq(attributes/a,1),eq(attributes/b,2))");
        assertPureRql("eq(attributes/a,1)");
        assertPureRql("exists(attributes/a)");
    }

    private static void assertPureRql(final String rql) {
        final PartitionedFilters partitioned = TargetTopicFilter.partition(List.of(rql));

        assertThat(partitioned.getRqlExpressions()).containsExactly(rql);
        assertThat(partitioned.getPipelineExpressions()).isEmpty();
    }

    // ===== partition(): mixed parameter lists =====

    @Test
    public void partitionClassifiesMixedParamsPreservingOrder() {
        final PartitionedFilters partitioned = TargetTopicFilter.partition(List.of(
                "gt(attributes/x,5)",
                "fn:filter(header:a,'exists')",
                "fn:filter(header:b,'ne','x')"));

        assertThat(partitioned.getRqlExpressions()).containsExactly("gt(attributes/x,5)");
        assertThat(partitioned.hasRqlExpression()).isTrue();
        assertThat(partitioned.getPipelineExpressions())
                .containsExactly("fn:filter(header:a,'exists')", "fn:filter(header:b,'ne','x')");
    }

    @Test
    public void partitionCollectsMultipleRqlParamsWithoutThrowing() {
        // partition() applies no structural rules - the at-most-one-RQL rule is enforced by ConnectionValidator
        final PartitionedFilters partitioned =
                TargetTopicFilter.partition(List.of("eq(attributes/a,1)", "eq(attributes/b,2)"));

        assertThat(partitioned.getRqlExpressions()).containsExactly("eq(attributes/a,1)", "eq(attributes/b,2)");
        assertThat(partitioned.getPipelineExpressions()).isEmpty();
    }

    @Test
    public void partitionTrimsEachParam() {
        final PartitionedFilters partitioned = TargetTopicFilter.partition(
                List.of(" fn:filter(header:ditto-originator,'eq','x')", " gt(attributes/x,5) "));

        assertThat(partitioned.getPipelineExpressions())
                .containsExactly("fn:filter(header:ditto-originator,'eq','x')");
        assertThat(partitioned.getRqlExpressions()).containsExactly("gt(attributes/x,5)");
    }

    // ===== partition(): RQL params containing "|" or "fn:" stay intact (no splitting anymore) =====

    @Test
    public void partitionKeepsRqlWithPipeInPropertyPathIntact() {
        // an unquoted "|" is legal in RQL property paths - without any splitting the whole param stays one
        // (valid) RQL expression
        assertPureRql("eq(attributes/a|b,1)");
        assertPureRql("exists(attributes/a|b)");
        assertPureRql("like(attributes/x,'*|*')");
    }

    @Test
    public void partitionLegacyCombinedSyntaxStaysOneRqlExpression() {
        // the retired "<rql>|fn:..." single-param syntax is NOT split anymore: not starting with "fn:", the whole
        // param is classified as RQL and fails loudly at RQL validation time (ConnectionValidatorTest locks the
        // rejection)
        final String legacyCombined = "gt(attributes/x,5)|fn:filter(header:a,'exists')";
        final PartitionedFilters partitioned = TargetTopicFilter.partition(List.of(legacyCombined));

        assertThat(partitioned.getRqlExpressions()).containsExactly(legacyCombined);
        assertThat(partitioned.getPipelineExpressions()).isEmpty();
    }

    // ===== partition(): empty / whitespace-only params (a PRESENT empty RQL entry, not absent) =====

    @Test
    public void partitionEmptyParamYieldsPresentEmptyRqlEntryNotAbsent() {
        // regression lock: an empty filter param must NOT vanish - it stays a PRESENT (empty) RQL entry, which
        // lets ConnectionValidator route it into RQL validation and reject it with InvalidRqlExpressionException,
        // exactly as an empty filter was rejected before target topic pipeline filters existed.
        assertThat(TargetTopicFilter.partition(List.of("")).getRqlExpressions()).containsExactly("");
        assertThat(TargetTopicFilter.partition(List.of("   ")).getRqlExpressions()).containsExactly("");
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

    // ===== matchesPipelineFilter(): chained stages (AND semantics) =====

    @Test
    public void matchesPipelineFilterChainedStagesBothMatchPublishes() {
        final Signal<?> signal = thingModifiedWithHeaders(Map.of(
                "ditto-originator", "some:subject",
                "ditto-origin", "some-origin"));

        assertThat(TargetTopicFilter.matchesPipelineFilter(
                "fn:filter(header:ditto-originator,'eq','some:subject')" +
                        "|fn:filter(header:ditto-origin,'eq','some-origin')", signal, CONNECTION_ID)).isTrue();
    }

    @Test
    public void matchesPipelineFilterChainedStagesFirstNonMatchDrops() {
        final Signal<?> signal = thingModifiedWithHeaders(Map.of(
                "ditto-originator", "other:subject",
                "ditto-origin", "some-origin"));

        assertThat(TargetTopicFilter.matchesPipelineFilter(
                "fn:filter(header:ditto-originator,'eq','some:subject')" +
                        "|fn:filter(header:ditto-origin,'eq','some-origin')", signal, CONNECTION_ID)).isFalse();
    }

    @Test
    public void matchesPipelineFilterChainedStagesSecondNonMatchDrops() {
        final Signal<?> signal = thingModifiedWithHeaders(Map.of(
                "ditto-originator", "some:subject",
                "ditto-origin", "other-origin"));

        assertThat(TargetTopicFilter.matchesPipelineFilter(
                "fn:filter(header:ditto-originator,'eq','some:subject')" +
                        "|fn:filter(header:ditto-origin,'eq','some-origin')", signal, CONNECTION_ID)).isFalse();
    }

    // ===== validatePipelineFilter(): invalid expressions =====

    @Test
    public void validatePipelineFilterAcceptsSingleStage() {
        assertThatNoException().isThrownBy(() ->
                TargetTopicFilter.validatePipelineFilter("fn:filter(header:ditto-originator,'ne','x')",
                        DittoHeaders.empty()));
    }

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

    // ===== validatePipelineFilter(): chained stages, pipeline grammar limits =====

    @Test
    public void validatePipelineFilterAcceptsChainedStages() {
        assertThatNoException().isThrownBy(() ->
                TargetTopicFilter.validatePipelineFilter(
                        "fn:filter(header:a,'exists')|fn:filter(header:b,'exists')", DittoHeaders.empty()));
    }

    @Test
    public void validatePipelineFilterRejectsChainedParamWithUnknownFunctionStage() {
        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class).isThrownBy(() ->
                TargetTopicFilter.validatePipelineFilter(
                        "fn:filter(header:a,'exists')|fn:unknownfn('x')", DittoHeaders.empty()));
    }

    @Test
    public void validatePipelineFilterAcceptsTenChainedStagesButRejectsEleven() {
        // the resolver's pipeline grammar caps a pipeline at 10 fn: stages (the internal fn:default seed does
        // not eat into the user's budget: seed + 10 user stages is exactly the grammar's 11-element maximum)
        final String tenStages = String.join("|", Collections.nCopies(10, "fn:filter(header:a,'exists')"));
        assertThatNoException().isThrownBy(() ->
                TargetTopicFilter.validatePipelineFilter(tenStages, DittoHeaders.empty()));

        final String elevenStages = String.join("|", Collections.nCopies(11, "fn:filter(header:a,'exists')"));
        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class).isThrownBy(() ->
                TargetTopicFilter.validatePipelineFilter(elevenStages, DittoHeaders.empty()));
    }

    @Test
    public void validatePipelineFilterAcceptsQuotedPipeInsideChainedStages() {
        // the resolver's stage split is quote-aware: the '|' inside 'a|b' must not be taken for a stage separator
        assertThatNoException().isThrownBy(() ->
                TargetTopicFilter.validatePipelineFilter(
                        "fn:filter(header:a,'eq','a|b')|fn:filter(header:b,'exists')", DittoHeaders.empty()));
    }

    @Test
    public void validatePipelineFilterRejectsTrailingPipe() {
        // rejected by the resolver's pipeline grammar (empty trailing stage), no custom scan involved
        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class).isThrownBy(() ->
                TargetTopicFilter.validatePipelineFilter("fn:filter(header:a,'exists')|", DittoHeaders.empty()));
    }

    @Test
    public void validatePipelineFilterAcceptsQuotedPipeInSingleQuotedConstant() {
        assertThatNoException().isThrownBy(() ->
                TargetTopicFilter.validatePipelineFilter("fn:filter(header:a,'eq','a|b')", DittoHeaders.empty()));
    }

    @Test
    public void validatePipelineFilterAcceptsQuotedPipeInDoubleQuotedConstant() {
        assertThatNoException().isThrownBy(() ->
                TargetTopicFilter.validatePipelineFilter("fn:filter(header:a,'eq',\"a|b\")", DittoHeaders.empty()));
    }

    @Test
    public void validatePipelineFilterTrailingBackslashDoesNotThrowUnexpectedly() {
        // a trailing backslash must never escape the documented exception contract; the resolver validation may
        // still reject the expression, but only ever with the documented exception type
        final Throwable throwable = catchThrowable(() ->
                TargetTopicFilter.validatePipelineFilter("fn:filter(header:a,'exists')\\", DittoHeaders.empty()));

        if (throwable != null) {
            assertThat(throwable).isInstanceOf(ConnectionConfigurationInvalidException.class);
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
