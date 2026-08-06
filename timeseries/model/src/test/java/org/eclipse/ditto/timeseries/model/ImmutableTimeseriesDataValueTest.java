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

import java.time.Instant;

import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit tests for {@link ImmutableTimeseriesDataValue}.
 */
public final class ImmutableTimeseriesDataValueTest {

    private static final Instant TIMESTAMP = Instant.parse("2026-01-15T10:30:00Z");
    private static final JsonValue VALUE = JsonValue.of(23.5);

    @Test
    public void hashCodeAndEqualsContract() {
        EqualsVerifier.forClass(ImmutableTimeseriesDataValue.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void factoryCreatesNonGapValue() {
        final TimeseriesDataValue underTest = TimeseriesDataValue.of(TIMESTAMP, VALUE);

        assertThat(underTest.getTimestamp()).isEqualTo(TIMESTAMP);
        assertThat(underTest.getValue()).contains(VALUE);
        assertThat(underTest.isGap()).isFalse();
    }

    @Test
    public void gapFactoryCreatesGapWithValue() {
        final JsonValue filledValue = JsonValue.of(0);

        final TimeseriesDataValue underTest = TimeseriesDataValue.gap(TIMESTAMP, filledValue);

        assertThat(underTest.getTimestamp()).isEqualTo(TIMESTAMP);
        assertThat(underTest.getValue()).contains(filledValue);
        assertThat(underTest.isGap()).isTrue();
    }

    @Test
    public void gapFactoryAcceptsNullValueForFillStrategyNull() {
        final TimeseriesDataValue underTest = TimeseriesDataValue.gap(TIMESTAMP, null);

        assertThat(underTest.getValue()).isEmpty();
        assertThat(underTest.isGap()).isTrue();
    }

    @Test
    public void nonGapFactoryRejectsNullValue() {
        assertThatNullPointerException().isThrownBy(() -> TimeseriesDataValue.of(TIMESTAMP, null));
    }

    @Test
    public void nonGapFactoryRejectsNullTimestamp() {
        assertThatNullPointerException().isThrownBy(() -> TimeseriesDataValue.of(null, VALUE));
    }

    @Test
    public void gapFactoryRejectsNullTimestamp() {
        assertThatNullPointerException().isThrownBy(() -> TimeseriesDataValue.gap(null, VALUE));
    }

    @Test
    public void toJsonContainsTimestampAndValue() {
        final TimeseriesDataValue underTest = TimeseriesDataValue.of(TIMESTAMP, VALUE);

        final JsonObject json = underTest.toJson();

        assertThat(json.getValue("t")).contains(JsonValue.of(TIMESTAMP.toString()));
        assertThat(json.getValue("v")).contains(VALUE);
    }

    @Test
    public void toJsonOmitsGapFlagForNonGapValue() {
        final TimeseriesDataValue underTest = TimeseriesDataValue.of(TIMESTAMP, VALUE);

        final JsonObject json = underTest.toJson();

        assertThat(json.contains("_gap")).isFalse();
    }

    @Test
    public void toJsonIncludesGapFlagForGapValue() {
        final TimeseriesDataValue underTest = TimeseriesDataValue.gap(TIMESTAMP, JsonValue.of(0));

        final JsonObject json = underTest.toJson();

        assertThat(json.getValue("_gap")).contains(JsonValue.of(true));
    }

    @Test
    public void toJsonEmitsExplicitNullForFillStrategyNullGap() {
        final TimeseriesDataValue underTest = TimeseriesDataValue.gap(TIMESTAMP, null);

        final JsonObject json = underTest.toJson();

        assertThat(json.getValue("v")).contains(JsonValue.nullLiteral());
        assertThat(json.getValue("_gap")).contains(JsonValue.of(true));
    }

    @Test
    public void roundTripPreservesNonGapValue() {
        final TimeseriesDataValue original = TimeseriesDataValue.of(TIMESTAMP, VALUE);

        final TimeseriesDataValue reconstructed = TimeseriesDataValue.fromJson(original.toJson());

        assertThat(reconstructed).isEqualTo(original);
    }

    @Test
    public void roundTripPreservesGapWithValue() {
        final TimeseriesDataValue original = TimeseriesDataValue.gap(TIMESTAMP, JsonValue.of(0));

        final TimeseriesDataValue reconstructed = TimeseriesDataValue.fromJson(original.toJson());

        assertThat(reconstructed).isEqualTo(original);
    }

    @Test
    public void roundTripPreservesNullGap() {
        final TimeseriesDataValue original = TimeseriesDataValue.gap(TIMESTAMP, null);

        final TimeseriesDataValue reconstructed = TimeseriesDataValue.fromJson(original.toJson());

        assertThat(reconstructed).isEqualTo(original);
    }

    @Test
    public void fromJsonRejectsInvalidTimestamp() {
        final JsonObject json = JsonFactory.newObjectBuilder()
                .set("t", "not-an-instant")
                .set("v", VALUE)
                .build();

        assertThatExceptionOfType(DittoJsonException.class)
                .isThrownBy(() -> TimeseriesDataValue.fromJson(json));
    }

    @Test
    public void fromJsonRejectsNullInput() {
        assertThatNullPointerException().isThrownBy(() -> TimeseriesDataValue.fromJson(null));
    }
}
