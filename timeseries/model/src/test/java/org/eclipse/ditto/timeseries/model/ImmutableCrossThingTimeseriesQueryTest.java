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
package org.eclipse.ditto.timeseries.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit tests for {@link ImmutableCrossThingTimeseriesQuery}.
 */
public final class ImmutableCrossThingTimeseriesQueryTest {

    private static final String NAMESPACE = "io.beyonnex.smartheating";
    private static final List<JsonPointer> PATHS = Collections.singletonList(
            JsonPointer.of("/features/circuit/properties/flowTemperature"));
    private static final Instant FROM = Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-07-02T00:00:00Z");
    private static final Duration STEP = Duration.ofHours(1);
    private static final Aggregation AGG = Aggregation.AVG;

    private static CrossThingTimeseriesQuery minimal() {
        return CrossThingTimeseriesQuery.of(NAMESPACE, PATHS, FROM, TO, STEP, AGG,
                Collections.<GroupBy>emptyList(), null,
                null, null, null);
    }

    @Test
    public void hashCodeAndEqualsContract() {
        EqualsVerifier.forClass(ImmutableCrossThingTimeseriesQuery.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void factoryCreatesInstanceWithAllFields() {
        final String filter = "eq(attributes/building,'A')";
        final List<GroupBy> groupBy = Arrays.asList(GroupBy.tag("building"), GroupBy.thingId());

        final CrossThingTimeseriesQuery underTest = CrossThingTimeseriesQuery.of(NAMESPACE, PATHS,
                FROM, TO, STEP, AGG, groupBy, filter, ZoneId.of("Europe/Berlin"),
                FillStrategy.LINEAR, 42);

        assertThat(underTest.getNamespace()).isEqualTo(NAMESPACE);
        assertThat(underTest.getPaths()).containsExactlyElementsOf(PATHS);
        assertThat(underTest.getFrom()).isEqualTo(FROM);
        assertThat(underTest.getTo()).isEqualTo(TO);
        assertThat(underTest.getStep()).isEqualTo(STEP);
        assertThat((Object) underTest.getAggregation()).isEqualTo(AGG);
        assertThat(underTest.getGroupBy()).containsExactlyElementsOf(groupBy);
        assertThat(underTest.getFilter()).contains(filter);
        assertThat(underTest.getTimezone()).contains(ZoneId.of("Europe/Berlin"));
        assertThat(underTest.getFillStrategy()).contains(FillStrategy.LINEAR);
        assertThat(underTest.getMaxGroups()).contains(42);
    }

    @Test
    public void optionalFieldsAreAbsentOnMinimalQuery() {
        final CrossThingTimeseriesQuery underTest = minimal();

        assertThat(underTest.getGroupBy()).isEmpty();
        assertThat(underTest.getFilter()).isEmpty();
        assertThat(underTest.getTimezone()).isEmpty();
        assertThat(underTest.getFillStrategy()).isEmpty();
        assertThat(underTest.getMaxGroups()).isEmpty();
    }

    @Test
    public void namespaceIsTrimmed() {
        final CrossThingTimeseriesQuery underTest = CrossThingTimeseriesQuery.of("  " + NAMESPACE + " ",
                PATHS, FROM, TO, STEP, AGG, Collections.<GroupBy>emptyList(),
                null, null, null, null);

        assertThat(underTest.getNamespace()).isEqualTo(NAMESPACE);
    }

    @Test
    public void jsonRoundTripsWithAllFields() {
        final String filter = "and(eq(attributes/building,'A'),eq(attributes/floor,'2'))";
        final CrossThingTimeseriesQuery underTest = CrossThingTimeseriesQuery.of(NAMESPACE, PATHS,
                FROM, TO, STEP, AGG, Arrays.asList(GroupBy.tag("building"), GroupBy.thingId()),
                filter, ZoneId.of("Europe/Berlin"), FillStrategy.PREVIOUS, 7);

        final JsonObject json = underTest.toJson();

        assertThat(CrossThingTimeseriesQuery.fromJson(json)).isEqualTo(underTest);
    }

    @Test
    public void jsonRoundTripsMinimalQuery() {
        final CrossThingTimeseriesQuery underTest = minimal();

        assertThat(CrossThingTimeseriesQuery.fromJson(underTest.toJson())).isEqualTo(underTest);
    }

    @Test
    public void toJsonOmitsAbsentOptionalFields() {
        final JsonObject json = minimal().toJson();

        assertThat(json.getValue("groupBy")).isEmpty();
        assertThat(json.getValue("filter")).isEmpty();
        assertThat(json.getValue("timezone")).isEmpty();
        assertThat(json.getValue("fillStrategy")).isEmpty();
        assertThat(json.getValue("maxGroups")).isEmpty();
    }

    @Test
    public void rejectsBlankNamespace() {
        assertThatExceptionOfType(TimeseriesQueryInvalidException.class)
                .isThrownBy(() -> CrossThingTimeseriesQuery.of("   ", PATHS, FROM, TO, STEP, AGG,
                        Collections.<GroupBy>emptyList(), null,
                        null, null, null))
                .withMessageContaining("non-empty 'namespaces'");
    }

    @Test
    public void rejectsEmptyPaths() {
        assertThatExceptionOfType(TimeseriesQueryInvalidException.class)
                .isThrownBy(() -> CrossThingTimeseriesQuery.of(NAMESPACE,
                        Collections.<JsonPointer>emptyList(), FROM, TO, STEP, AGG,
                        Collections.<GroupBy>emptyList(), null,
                        null, null, null))
                .withMessageContaining("at least one path");
    }

    /**
     * The same bound the single-Thing query enforces. It matters more here: every path is a separate
     * grouped scan over the whole namespace, so the path count multiplies an already namespace-wide
     * fan-out.
     */
    @Test
    public void rejectsMoreThanMaxPaths() {
        final List<JsonPointer> tooMany = new ArrayList<>();
        for (int i = 0; i <= TimeseriesQuery.MAX_PATHS; i++) {
            tooMany.add(JsonPointer.of("/features/f/properties/p" + i));
        }

        assertThatExceptionOfType(TimeseriesQueryInvalidException.class)
                .isThrownBy(() -> CrossThingTimeseriesQuery.of(NAMESPACE, tooMany, FROM, TO, STEP, AGG,
                        Collections.<GroupBy>emptyList(), null,
                        null, null, null))
                .withMessageContaining("at most <" + TimeseriesQuery.MAX_PATHS + "> paths");
    }

    @Test
    public void acceptsExactlyMaxPaths() {
        final List<JsonPointer> exactly = new ArrayList<>();
        for (int i = 0; i < TimeseriesQuery.MAX_PATHS; i++) {
            exactly.add(JsonPointer.of("/features/f/properties/p" + i));
        }

        final CrossThingTimeseriesQuery underTest = CrossThingTimeseriesQuery.of(NAMESPACE, exactly,
                FROM, TO, STEP, AGG, Collections.<GroupBy>emptyList(), null, null, null, null);

        assertThat(underTest.getPaths()).hasSize(TimeseriesQuery.MAX_PATHS);
    }

    @Test
    public void rejectsFromNotBeforeTo() {
        assertThatExceptionOfType(TimeseriesQueryInvalidException.class)
                .isThrownBy(() -> CrossThingTimeseriesQuery.of(NAMESPACE, PATHS, TO, FROM, STEP, AGG,
                        Collections.<GroupBy>emptyList(), null,
                        null, null, null))
                .withMessageContaining("must be strictly before");
    }

    @Test
    public void rejectsNonPositiveStep() {
        assertThatExceptionOfType(TimeseriesQueryInvalidException.class)
                .isThrownBy(() -> CrossThingTimeseriesQuery.of(NAMESPACE, PATHS, FROM, TO,
                        Duration.ZERO, AGG, Collections.<GroupBy>emptyList(),
                        null, null, null, null))
                .withMessageContaining("positive duration");
    }

    /**
     * A cross-Thing read without a bucketed aggregation would stream every raw point of every
     * matching Thing — the exact fan-out this endpoint exists to bound.
     */
    @Test
    public void rejectsWindowFunctionAggregations() {
        for (final Aggregation windowFunction : Arrays.asList(Aggregation.DERIVATIVE,
                Aggregation.RATE, Aggregation.INTEGRAL, Aggregation.PERCENTILE)) {

            assertThatExceptionOfType(TimeseriesQueryInvalidException.class)
                    .as("aggregation <%s> must be rejected", windowFunction)
                    .isThrownBy(() -> CrossThingTimeseriesQuery.of(NAMESPACE, PATHS, FROM, TO, STEP,
                            windowFunction, Collections.<GroupBy>emptyList(),
                            null, null, null, null))
                    .withMessageContaining("window function");
        }
    }

    @Test
    public void acceptsEveryBucketedAggregation() {
        for (final Aggregation aggregation : Aggregation.values()) {
            if (aggregation.requiresStep()) {
                final CrossThingTimeseriesQuery query = CrossThingTimeseriesQuery.of(NAMESPACE, PATHS,
                        FROM, TO, STEP, aggregation, Collections.<GroupBy>emptyList(),
                        null, null, null, null);

                assertThat((Object) query.getAggregation()).isEqualTo(aggregation);
            }
        }
    }

    @Test
    public void rejectsDuplicateGroupByDimensions() {
        assertThatExceptionOfType(TimeseriesQueryInvalidException.class)
                .isThrownBy(() -> CrossThingTimeseriesQuery.of(NAMESPACE, PATHS, FROM, TO, STEP, AGG,
                        Arrays.asList(GroupBy.tag("building"), GroupBy.tag("building")),
                        null, null, null, null))
                .withMessageContaining("Duplicate groupBy dimension");
    }

    @Test
    public void allowsDistinctTagDimensions() {
        final CrossThingTimeseriesQuery underTest = CrossThingTimeseriesQuery.of(NAMESPACE, PATHS,
                FROM, TO, STEP, AGG, Arrays.asList(GroupBy.tag("building"), GroupBy.tag("floor")),
                null, null, null, null);

        assertThat(underTest.getGroupBy()).hasSize(2);
    }

    @Test
    public void rejectsNonPositiveMaxGroups() {
        assertThatExceptionOfType(TimeseriesQueryInvalidException.class)
                .isThrownBy(() -> CrossThingTimeseriesQuery.of(NAMESPACE, PATHS, FROM, TO, STEP, AGG,
                        Collections.<GroupBy>emptyList(), null,
                        null, null, 0))
                .withMessageContaining("must be positive");
    }

    @Test
    public void rejectsMaxGroupsAboveCeiling() {
        assertThatExceptionOfType(TimeseriesQueryInvalidException.class)
                .isThrownBy(() -> CrossThingTimeseriesQuery.of(NAMESPACE, PATHS, FROM, TO, STEP, AGG,
                        Collections.<GroupBy>emptyList(), null,
                        null, null, CrossThingTimeseriesQuery.MAX_GROUPS_CEILING + 1))
                .withMessageContaining("must not exceed");
    }

    @Test
    public void acceptsMaxGroupsAtCeiling() {
        final CrossThingTimeseriesQuery underTest = CrossThingTimeseriesQuery.of(NAMESPACE, PATHS,
                FROM, TO, STEP, AGG, Collections.<GroupBy>emptyList(),
                null, null, null,
                CrossThingTimeseriesQuery.MAX_GROUPS_CEILING);

        assertThat(underTest.getMaxGroups()).contains(CrossThingTimeseriesQuery.MAX_GROUPS_CEILING);
    }

    @Test
    public void rejectsNullRequiredArguments() {
        assertThatNullPointerException().isThrownBy(() -> CrossThingTimeseriesQuery.of(null, PATHS,
                FROM, TO, STEP, AGG, Collections.<GroupBy>emptyList(),
                null, null, null, null));
        assertThatNullPointerException().isThrownBy(() -> CrossThingTimeseriesQuery.of(NAMESPACE,
                null, FROM, TO, STEP, AGG, Collections.<GroupBy>emptyList(),
                null, null, null, null));
        assertThatNullPointerException().isThrownBy(() -> CrossThingTimeseriesQuery.of(NAMESPACE,
                PATHS, FROM, TO, null, AGG, Collections.<GroupBy>emptyList(),
                null, null, null, null));
        assertThatNullPointerException().isThrownBy(() -> CrossThingTimeseriesQuery.of(NAMESPACE,
                PATHS, FROM, TO, STEP, null, Collections.<GroupBy>emptyList(),
                null, null, null, null));
    }

    @Test
    public void returnedCollectionsAreUnmodifiable() {
        final CrossThingTimeseriesQuery underTest = CrossThingTimeseriesQuery.of(NAMESPACE, PATHS,
                FROM, TO, STEP, AGG, Collections.singletonList(GroupBy.thingId()),
                "eq(attributes/building,'A')", null, null, null);

        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> underTest.getPaths().add(JsonPointer.of("/attributes/x")));
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> underTest.getGroupBy().add(GroupBy.path()));
    }
}
