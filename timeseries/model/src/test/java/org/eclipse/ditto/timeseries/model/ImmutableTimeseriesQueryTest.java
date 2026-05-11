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
import java.util.List;
import java.util.Optional;

import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.ThingId;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit tests for {@link ImmutableTimeseriesQuery}.
 */
public final class ImmutableTimeseriesQueryTest {

    private static final ThingId THING_ID = ThingId.of("org.eclipse.ditto", "sensor-1");
    private static final List<JsonPointer> PATHS = Collections.unmodifiableList(Arrays.asList(
            JsonPointer.of("/features/environment/properties/temperature"),
            JsonPointer.of("/features/environment/properties/humidity")));
    private static final Instant FROM = Instant.parse("2026-01-14T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-01-15T00:00:00Z");
    private static final Duration STEP = Duration.ofHours(1);
    private static final Aggregation AGGREGATION = Aggregation.AVG;
    private static final FillStrategy FILL_STRATEGY = FillStrategy.PREVIOUS;
    private static final Integer LIMIT = 1000;
    private static final ZoneId TIMEZONE = ZoneId.of("Europe/Berlin");

    @Test
    public void hashCodeAndEqualsContract() {
        EqualsVerifier.forClass(ImmutableTimeseriesQuery.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void factoryCreatesInstanceWithAllFields() {
        final TimeseriesQuery underTest = TimeseriesQuery.of(
                THING_ID, PATHS, FROM, TO, STEP, AGGREGATION, FILL_STRATEGY, LIMIT, TIMEZONE);

        assertThat((Object) underTest.getThingId()).isEqualTo(THING_ID);
        final List<JsonPointer> actualPaths = underTest.getPaths();
        assertThat(actualPaths).containsExactlyElementsOf(PATHS);
        assertThat(underTest.getFrom()).isEqualTo(FROM);
        assertThat(underTest.getTo()).isEqualTo(TO);
        assertThat(underTest.getStep()).contains(STEP);
        final Optional<Aggregation> agg = underTest.getAggregation();
        assertThat(agg).contains(AGGREGATION);
        final Optional<FillStrategy> fill = underTest.getFillStrategy();
        assertThat(fill).contains(FILL_STRATEGY);
        final Optional<Integer> lim = underTest.getLimit();
        assertThat(lim).contains(LIMIT);
        assertThat(underTest.getTimezone()).contains(TIMEZONE);
    }

    @Test
    public void shortFactoryCreatesRawQuery() {
        final TimeseriesQuery underTest = TimeseriesQuery.of(THING_ID, PATHS, FROM, TO);

        assertThat(underTest.getStep()).isEmpty();
        assertThat(underTest.getAggregation()).isEmpty();
        assertThat(underTest.getFillStrategy()).isEmpty();
        assertThat(underTest.getLimit()).isEmpty();
        assertThat(underTest.getTimezone()).isEmpty();
    }

    @Test
    public void factoryAcceptsEmptyPaths() {
        final TimeseriesQuery underTest =
                TimeseriesQuery.of(THING_ID, Collections.emptyList(), FROM, TO);

        assertThat(underTest.getPaths()).isEmpty();
    }

    @Test
    public void factoryAcceptsAllNullableFieldsAsNull() {
        final TimeseriesQuery underTest = TimeseriesQuery.of(
                THING_ID, PATHS, FROM, TO, null, null, null, null, null);

        assertThat(underTest.getStep()).isEmpty();
        assertThat(underTest.getAggregation()).isEmpty();
        assertThat(underTest.getFillStrategy()).isEmpty();
        assertThat(underTest.getLimit()).isEmpty();
        assertThat(underTest.getTimezone()).isEmpty();
    }

    @Test
    public void factoryRejectsNullThingId() {
        assertThatNullPointerException().isThrownBy(() -> TimeseriesQuery.of(
                null, PATHS, FROM, TO, STEP, AGGREGATION, FILL_STRATEGY, LIMIT, TIMEZONE));
    }

    @Test
    public void factoryRejectsNullPaths() {
        assertThatNullPointerException().isThrownBy(() -> TimeseriesQuery.of(
                THING_ID, null, FROM, TO, STEP, AGGREGATION, FILL_STRATEGY, LIMIT, TIMEZONE));
    }

    @Test
    public void factoryRejectsNullFrom() {
        assertThatNullPointerException().isThrownBy(() -> TimeseriesQuery.of(
                THING_ID, PATHS, null, TO, STEP, AGGREGATION, FILL_STRATEGY, LIMIT, TIMEZONE));
    }

    @Test
    public void factoryRejectsNullTo() {
        assertThatNullPointerException().isThrownBy(() -> TimeseriesQuery.of(
                THING_ID, PATHS, FROM, null, STEP, AGGREGATION, FILL_STRATEGY, LIMIT, TIMEZONE));
    }

    @Test
    public void getPathsReturnsAnUnmodifiableList() {
        final TimeseriesQuery underTest = TimeseriesQuery.of(THING_ID, PATHS, FROM, TO);

        assertThat(underTest.getPaths()).isUnmodifiable();
    }

    @Test
    public void factoryDefensivelyCopiesPaths() {
        final List<JsonPointer> mutable = new ArrayList<>();
        mutable.add(JsonPointer.of("/features/env/properties/temperature"));

        final TimeseriesQuery underTest = TimeseriesQuery.of(THING_ID, mutable, FROM, TO);

        mutable.add(JsonPointer.of("/features/env/properties/humidity"));

        assertThat(underTest.getPaths()).hasSize(1);
    }

    @Test
    public void toJsonContainsAllNonDefaultFields() {
        final TimeseriesQuery underTest = TimeseriesQuery.of(
                THING_ID, PATHS, FROM, TO, STEP, AGGREGATION, FILL_STRATEGY, LIMIT, TIMEZONE);

        final JsonObject json = underTest.toJson();

        assertThat(json.getValue("thingId")).contains(JsonValue.of(THING_ID.toString()));
        assertThat(json.getValue("paths")).isPresent();
        assertThat(json.getValue("from")).contains(JsonValue.of("2026-01-14T00:00:00Z"));
        assertThat(json.getValue("to")).contains(JsonValue.of("2026-01-15T00:00:00Z"));
        assertThat(json.getValue("step")).contains(JsonValue.of("PT1H"));
        assertThat(json.getValue("aggregation")).contains(JsonValue.of("avg"));
        assertThat(json.getValue("fillStrategy")).contains(JsonValue.of("previous"));
        assertThat(json.getValue("limit")).contains(JsonValue.of(LIMIT));
        assertThat(json.getValue("timezone")).contains(JsonValue.of("Europe/Berlin"));
    }

    @Test
    public void toJsonOmitsAllOptionalFieldsWhenAbsent() {
        final TimeseriesQuery underTest = TimeseriesQuery.of(THING_ID, PATHS, FROM, TO);

        final JsonObject json = underTest.toJson();

        assertThat(json.contains("step")).isFalse();
        assertThat(json.contains("aggregation")).isFalse();
        assertThat(json.contains("fillStrategy")).isFalse();
        assertThat(json.contains("limit")).isFalse();
        assertThat(json.contains("timezone")).isFalse();
    }

    @Test
    public void roundTripPreservesAllFields() {
        final TimeseriesQuery original = TimeseriesQuery.of(
                THING_ID, PATHS, FROM, TO, STEP, AGGREGATION, FILL_STRATEGY, LIMIT, TIMEZONE);

        final JsonObject json = original.toJson();
        final TimeseriesQuery reconstructed = TimeseriesQuery.fromJson(json);

        assertThat(reconstructed).isEqualTo(original);
    }

    @Test
    public void roundTripWithoutOptionalFieldsPreservesEquality() {
        final TimeseriesQuery original = TimeseriesQuery.of(THING_ID, PATHS, FROM, TO);

        final JsonObject json = original.toJson();
        final TimeseriesQuery reconstructed = TimeseriesQuery.fromJson(json);

        assertThat(reconstructed).isEqualTo(original);
    }

    @Test
    public void fromJsonAppliesDefaultsForOptionalFields() {
        final JsonObject json = baseJson().build();

        final TimeseriesQuery underTest = TimeseriesQuery.fromJson(json);

        assertThat(underTest.getStep()).isEmpty();
        assertThat(underTest.getAggregation()).isEmpty();
        assertThat(underTest.getFillStrategy()).isEmpty();
        assertThat(underTest.getLimit()).isEmpty();
        assertThat(underTest.getTimezone()).isEmpty();
    }

    @Test
    public void fromJsonRejectsMissingThingId() {
        final JsonObject json = JsonFactory.newObjectBuilder()
                .set("paths", JsonFactory.newArrayBuilder().add("/foo").build())
                .set("from", FROM.toString())
                .set("to", TO.toString())
                .build();

        assertThatExceptionOfType(JsonMissingFieldException.class)
                .isThrownBy(() -> TimeseriesQuery.fromJson(json));
    }

    @Test
    public void fromJsonRejectsInvalidFrom() {
        final JsonObject json = baseJson().set("from", "not-an-instant").build();

        assertThatExceptionOfType(DittoJsonException.class)
                .isThrownBy(() -> TimeseriesQuery.fromJson(json));
    }

    @Test
    public void fromJsonRejectsInvalidStep() {
        final JsonObject json = baseJson().set("step", "1 hour").build();

        assertThatExceptionOfType(DittoJsonException.class)
                .isThrownBy(() -> TimeseriesQuery.fromJson(json));
    }

    @Test
    public void fromJsonRejectsUnknownAggregation() {
        final JsonObject json = baseJson().set("aggregation", "bogus").build();

        assertThatExceptionOfType(DittoJsonException.class)
                .isThrownBy(() -> TimeseriesQuery.fromJson(json));
    }

    @Test
    public void fromJsonRejectsUnknownFillStrategy() {
        final JsonObject json = baseJson().set("fillStrategy", "interpolate-magic").build();

        assertThatExceptionOfType(DittoJsonException.class)
                .isThrownBy(() -> TimeseriesQuery.fromJson(json));
    }

    @Test
    public void fromJsonRejectsInvalidTimezone() {
        final JsonObject json = baseJson().set("timezone", "Mars/Olympus_Mons").build();

        assertThatExceptionOfType(DittoJsonException.class)
                .isThrownBy(() -> TimeseriesQuery.fromJson(json));
    }

    @Test
    public void fromJsonRejectsNonStringPathElement() {
        final JsonObject json = baseJson().set("paths", JsonFactory.newArrayBuilder().add(42).build()).build();

        assertThatExceptionOfType(DittoJsonException.class)
                .isThrownBy(() -> TimeseriesQuery.fromJson(json));
    }

    @Test
    public void fromJsonRejectsNullInput() {
        assertThatNullPointerException().isThrownBy(() -> TimeseriesQuery.fromJson(null));
    }

    @Test
    public void toStringIncludesAllFields() {
        final TimeseriesQuery underTest = TimeseriesQuery.of(
                THING_ID, PATHS, FROM, TO, STEP, AGGREGATION, FILL_STRATEGY, LIMIT, TIMEZONE);

        final String s = underTest.toString();

        assertThat(s)
                .contains(THING_ID.toString())
                .contains(FROM.toString())
                .contains(TO.toString())
                .contains(STEP.toString())
                .contains(AGGREGATION.toString())
                .contains(FILL_STRATEGY.toString())
                .contains(String.valueOf(LIMIT))
                .contains(TIMEZONE.toString());
    }

    private static JsonObjectBuilder baseJson() {
        return JsonFactory.newObjectBuilder()
                .set("thingId", THING_ID.toString())
                .set("paths", JsonFactory.newArrayBuilder()
                        .add("/features/environment/properties/temperature")
                        .add("/features/environment/properties/humidity")
                        .build())
                .set("from", FROM.toString())
                .set("to", TO.toString());
    }
}
