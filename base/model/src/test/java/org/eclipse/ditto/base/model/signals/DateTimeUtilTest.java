/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.signals;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoField;

import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests {@link DateTimeUtil}.
 */
public class DateTimeUtilTest {

    @BeforeClass
    public static void dateTimeUtilShouldNotCrashDuringInitialization() {
        DateTimeUtil.OFFSET_DATE_TIME.getClass();
    }

    private void assert19700123T012345_1337(final OffsetDateTime time) {
        assertThat(time.getYear()).isEqualTo(1970);
        assertThat(time.getMonthValue()).isEqualTo(1);
        assertThat(time.getDayOfMonth()).isEqualTo(23);
        assertThat(time.getHour()).isEqualTo(1);
        assertThat(time.getMinute()).isEqualTo(23);
        assertThat(time.getSecond()).isEqualTo(45);
        assertThat(time.getOffset().getLong(ChronoField.OFFSET_SECONDS)).isEqualTo((13 * 60 + 37) * -60L);
    }

    @Test
    public void parseExpandedFormat() {
        final OffsetDateTime time = DateTimeUtil.parseOffsetDateTime("1970-01-23T01:23:45-13:37");
        assert19700123T012345_1337(time);
    }

    @Test
    public void parseBasicFormat() {
        final OffsetDateTime time = DateTimeUtil.parseOffsetDateTime("19700123T012345-1337");
        assert19700123T012345_1337(time);
    }

    @Test
    public void parseShortExpandedFormat() {
        final OffsetDateTime time = DateTimeUtil.parseOffsetDateTime("1970-01-01T01+13");
        assertThat(time.toEpochSecond()).isEqualTo(-12 * 60 * 60);
    }

    @Test
    public void parseShortBasicFormat() {
        final OffsetDateTime time = DateTimeUtil.parseOffsetDateTime("1970-01-01T01+13");
        assertThat(time.toEpochSecond()).isEqualTo(-12 * 60 * 60);
    }

    @Test
    public void parseZuluTime() {
        final OffsetDateTime time = DateTimeUtil.parseOffsetDateTime("19700123T012345Z");
        assertThat(time.getOffset().getLong(ChronoField.OFFSET_SECONDS)).isZero();
    }

    @Test(expected = DateTimeException.class)
    public void monthTooBig() {
        DateTimeUtil.parseOffsetDateTime("1970-13-01T00Z");
    }

    @Test(expected = DateTimeException.class)
    public void dayTooBig() {
        DateTimeUtil.parseOffsetDateTime("1970-12-32T00Z");
    }

    @Test(expected = DateTimeException.class)
    public void hourTooBig() {
        DateTimeUtil.parseOffsetDateTime("1970-12-31T25Z");
    }

    @Test(expected = DateTimeException.class)
    public void minuteTooBig() {
        DateTimeUtil.parseOffsetDateTime("1970-12-31T23:60Z");
    }

    @Test(expected = DateTimeException.class)
    public void leapSecondsNotSupported() {
        final OffsetDateTime time = DateTimeUtil.parseOffsetDateTime("1972-06-30T23:59:60Z");
        assertThat(time.toEpochSecond()).isEqualTo(2287785600L);
    }

    @Test(expected = DateTimeException.class)
    public void secondTooBig() {
        DateTimeUtil.parseOffsetDateTime("1970-12-31T23:59:60Z");
    }

    @Test(expected = DateTimeException.class)
    public void offsetHourTooBig() {
        DateTimeUtil.parseOffsetDateTime("1970-12-31T23:59:59+19");
    }

    @Test(expected = DateTimeException.class)
    public void offsetMinuteTooBig() {
        DateTimeUtil.parseOffsetDateTime("1970-12-31T23:59:59+01:60");
    }

    @Test(expected = DateTimeException.class)
    public void offsetSecondsNotSupported() {
        final OffsetDateTime time = DateTimeUtil.parseOffsetDateTime("1970-12-31T23:59:59+03:59:59");
        assertThat(time.getLong(ChronoField.OFFSET_SECONDS)).isEqualTo(3 * 60 * 60 + 59 * 60 + 59);
    }

    @Test(expected = DateTimeException.class)
    public void offsetMillisecondsNotSupported() {
        DateTimeUtil.parseOffsetDateTime("1970-12-31T23:59:59+03:59:59.999");
    }

    @Test(expected = DateTimeException.class)
    public void notZuluTime() {
        DateTimeUtil.parseOffsetDateTime("1970-12-31T23:59:60X");
    }
}
