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
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit tests for {@link ImmutableTimeseriesResultMeta}.
 */
public final class ImmutableTimeseriesResultMetaTest {

    @Test
    public void hashCodeAndEqualsContract() {
        EqualsVerifier.forClass(ImmutableTimeseriesResultMeta.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void factoryCreatesInstanceWithUnit() {
        final TimeseriesResultMeta underTest = TimeseriesResultMeta.of(24, "cel", "number");

        assertThat(underTest.getCount()).isEqualTo(24);
        assertThat(underTest.getUnit()).contains("cel");
        assertThat(underTest.getDataType()).isEqualTo("number");
    }

    @Test
    public void factoryAcceptsNullUnit() {
        final TimeseriesResultMeta underTest = TimeseriesResultMeta.of(0, null, "string");

        assertThat(underTest.getUnit()).isEmpty();
    }

    @Test
    public void factoryAcceptsZeroCount() {
        final TimeseriesResultMeta underTest = TimeseriesResultMeta.of(0, null, "number");

        assertThat(underTest.getCount()).isZero();
    }

    @Test
    public void factoryRejectsNegativeCount() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> TimeseriesResultMeta.of(-1, null, "number"));
    }

    @Test
    public void factoryRejectsNullDataType() {
        assertThatNullPointerException()
                .isThrownBy(() -> TimeseriesResultMeta.of(0, null, null));
    }

    @Test
    public void toJsonContainsCountAndDataType() {
        final TimeseriesResultMeta underTest = TimeseriesResultMeta.of(24, "cel", "number");

        final JsonObject json = underTest.toJson();

        assertThat(json.getValue("count")).contains(JsonValue.of(24));
        assertThat(json.getValue("dataType")).contains(JsonValue.of("number"));
        assertThat(json.getValue("unit")).contains(JsonValue.of("cel"));
    }

    @Test
    public void toJsonOmitsUnitWhenNull() {
        final TimeseriesResultMeta underTest = TimeseriesResultMeta.of(24, null, "number");

        final JsonObject json = underTest.toJson();

        assertThat(json.contains("unit")).isFalse();
    }

    @Test
    public void roundTripPreservesAllFields() {
        final TimeseriesResultMeta original = TimeseriesResultMeta.of(24, "cel", "number");

        final TimeseriesResultMeta reconstructed = TimeseriesResultMeta.fromJson(original.toJson());

        assertThat(reconstructed).isEqualTo(original);
    }

    @Test
    public void roundTripWithoutUnitPreservesEquality() {
        final TimeseriesResultMeta original = TimeseriesResultMeta.of(0, null, "string");

        final TimeseriesResultMeta reconstructed = TimeseriesResultMeta.fromJson(original.toJson());

        assertThat(reconstructed).isEqualTo(original);
    }

    @Test
    public void fromJsonRejectsMissingCount() {
        final JsonObject json = JsonFactory.newObjectBuilder()
                .set("dataType", "number")
                .build();

        assertThatExceptionOfType(JsonMissingFieldException.class)
                .isThrownBy(() -> TimeseriesResultMeta.fromJson(json));
    }

    @Test
    public void fromJsonRejectsMissingDataType() {
        final JsonObject json = JsonFactory.newObjectBuilder()
                .set("count", 0)
                .build();

        assertThatExceptionOfType(JsonMissingFieldException.class)
                .isThrownBy(() -> TimeseriesResultMeta.fromJson(json));
    }

    @Test
    public void fromJsonRejectsNullInput() {
        assertThatNullPointerException().isThrownBy(() -> TimeseriesResultMeta.fromJson(null));
    }
}
