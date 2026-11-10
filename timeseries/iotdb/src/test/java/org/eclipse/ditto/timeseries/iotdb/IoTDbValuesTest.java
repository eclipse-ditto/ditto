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
package org.eclipse.ditto.timeseries.iotdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.timeseries.iotdb.IoTDbSql.TagType;
import org.eclipse.ditto.timeseries.iotdb.IoTDbValues.Typed;
import org.junit.Test;

/**
 * Unit tests for {@link IoTDbValues}.
 */
public final class IoTDbValuesTest {

    @Test
    public void scalarsMapToTheirTypedColumn() {
        assertThat(IoTDbValues.typed(JsonValue.of("x"))).contains(new Typed(TagType.STRING, "x"));
        assertThat(IoTDbValues.typed(JsonValue.of(2))).contains(new Typed(TagType.NUMBER, 2.0));
        assertThat(IoTDbValues.typed(JsonValue.of(2.5))).contains(new Typed(TagType.NUMBER, 2.5));
        assertThat(IoTDbValues.typed(JsonValue.of(true))).contains(new Typed(TagType.BOOLEAN, true));
        assertThat(IoTDbValues.typed(JsonValue.nullLiteral())).isEmpty();
    }

    @Test
    public void objectsAndArraysAreRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> IoTDbValues.typed(JsonObject.empty()));
        assertThatIllegalArgumentException().isThrownBy(() -> IoTDbValues.typed(JsonArray.empty()));
    }

    @Test
    public void integralDoublesReadBackAsIntegers() {
        assertThat(IoTDbValues.number(42.0)).isEqualTo(JsonValue.of(42));
        assertThat(IoTDbValues.number(1e12)).isEqualTo(JsonValue.of(1_000_000_000_000L));
        assertThat(IoTDbValues.number(22.5)).isEqualTo(JsonValue.of(22.5));
        assertThat(IoTDbValues.number(1e300)).isEqualTo(JsonValue.of(1e300));
    }

    @Test
    public void fromColumnsPicksTheSetColumn() {
        assertThat(IoTDbValues.fromColumns(1.5, null, null)).isEqualTo(JsonValue.of(1.5));
        assertThat(IoTDbValues.fromColumns(null, "on", null)).isEqualTo(JsonValue.of("on"));
        assertThat(IoTDbValues.fromColumns(null, null, false)).isEqualTo(JsonValue.of(false));
        assertThat(IoTDbValues.fromColumns(null, null, null)).isEqualTo(JsonValue.nullLiteral());
    }
}
