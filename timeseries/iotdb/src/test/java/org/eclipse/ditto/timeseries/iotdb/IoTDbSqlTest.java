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

import java.time.Duration;
import java.time.Instant;

import org.eclipse.ditto.timeseries.api.compute.TimeseriesComputeKernel;
import org.eclipse.ditto.timeseries.iotdb.IoTDbSql.TagType;
import org.junit.Test;

/**
 * Unit tests for {@link IoTDbSql}.
 */
public final class IoTDbSqlTest {

    @Test
    public void encodingKeepsLowerCaseLettersAndDigitsAndEscapesEverythingElse() {
        assertThat(IoTDbSql.encode("attributes/building")).isEqualTo("attributes_2fbuilding");
        assertThat(IoTDbSql.encode("Floor_1")).isEqualTo("_46loor_5f1");
        assertThat(IoTDbSql.encode("ä")).isEqualTo("_c3_a4");
    }

    @Test
    public void encodingIsInjectiveAcrossCaseAndUnderscores() {
        assertThat(IoTDbSql.encode("a_2f")).isNotEqualTo(IoTDbSql.encode("a/"));
        assertThat(IoTDbSql.encode("Floor")).isNotEqualTo(IoTDbSql.encode("floor"));
    }

    @Test
    public void tagColumnRoundTrips() {
        for (final String key : new String[]{"attributes/building", "O'Brien\"; DROP", "a_b", "Ünïcode/x", "s"}) {
            for (final TagType type : TagType.values()) {
                final String column = IoTDbSql.tagColumn(key, type);
                assertThat(column).matches("tg_[a-z0-9_]+_[snb]");
                assertThat(IoTDbSql.tagKeyOf(column)).contains(key);
                assertThat(IoTDbSql.tagTypeOf(column)).contains(type);
            }
        }
    }

    @Test
    public void baseColumnsAreNotTagColumns() {
        assertThat(IoTDbSql.tagKeyOf(IoTDbSql.COL_VALUE_DOUBLE)).isEmpty();
        assertThat(IoTDbSql.tagKeyOf("tg_")).isEmpty();
        assertThat(IoTDbSql.tagKeyOf("tg_ab_x")).isEmpty();
        assertThat(IoTDbSql.tagKeyOf("tg_a_zz_s")).isEmpty();
    }

    @Test
    public void tableNameEncodesTheNamespace() {
        assertThat(IoTDbSql.tableName("ts_", "org.eclipse.ditto")).isEqualTo("ts_org_2eeclipse_2editto");
        assertThat(IoTDbSql.tableName("ts_", "Org.x")).isNotEqualTo(IoTDbSql.tableName("ts_", "org.x"));
    }

    @Test
    public void quoteAcceptsOnlySafeIdentifiers() {
        assertThat(IoTDbSql.quote("tg_a_2fb_s")).isEqualTo("\"tg_a_2fb_s\"");
        assertThatIllegalArgumentException().isThrownBy(() -> IoTDbSql.quote("a\"b"));
        assertThatIllegalArgumentException().isThrownBy(() -> IoTDbSql.quote("A"));
    }

    @Test
    public void stringLiteralDoublesSingleQuotes() {
        assertThat(IoTDbSql.literal("x' OR '1'='1")).isEqualTo("'x'' OR ''1''=''1'");
        assertThat(IoTDbSql.literal("back\\slash \"q\"")).isEqualTo("'back\\slash \"q\"'");
    }

    @Test
    public void numberLiteralRejectsNonFiniteValues() {
        assertThat(IoTDbSql.literal(2.0)).isEqualTo("2.0");
        assertThat(IoTDbSql.literal(-1.5E-7)).isEqualTo("-1.5E-7");
        assertThatIllegalArgumentException().isThrownBy(() -> IoTDbSql.literal(Double.NaN));
        assertThatIllegalArgumentException().isThrownBy(() -> IoTDbSql.literal(Double.POSITIVE_INFINITY));
    }

    @Test
    public void instantLiteralIsEpochMillis() {
        assertThat(IoTDbSql.literal(Instant.parse("2026-01-14T10:00:00.123Z"))).isEqualTo("1768384800123");
    }

    @Test
    public void dateBinUsesTheKernelOrigin() {
        assertThat(IoTDbSql.dateBin(Duration.ofHours(1))).isEqualTo("date_bin(3600000ms, time, 946684800000)");
        final Instant t = Instant.parse("2026-01-14T07:13:00Z");
        final long step = Duration.ofHours(5).toMillis();
        final long expected = IoTDbSql.BUCKET_ORIGIN_MILLIS +
                Math.floorDiv(t.toEpochMilli() - IoTDbSql.BUCKET_ORIGIN_MILLIS, step) * step;
        assertThat(TimeseriesComputeKernel.bucketStart(t, Duration.ofHours(5), null))
                .isEqualTo(Instant.ofEpochMilli(expected));
    }

    @Test
    public void likePatternTranslatesWildcardsAndEscapesSqlOnes() {
        assertThat(IoTDbSql.likePattern("a*b?c")).isEqualTo("a%b_c");
        assertThat(IoTDbSql.likePattern("100%_\\")).isEqualTo("100\\%\\_\\\\");
    }

    @Test
    public void ttlIsMillisOrInfinite() {
        assertThat(IoTDbSql.ttl(Duration.ofDays(1))).isEqualTo("86400000");
        assertThat(IoTDbSql.ttl(null)).isEqualTo("'INF'");
    }
}
