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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit tests for {@link TimeseriesCursor}.
 */
public final class TimeseriesCursorTest {

    private static final Instant TIMESTAMP = Instant.parse("2026-01-15T10:30:00.123Z");
    private static final long REVISION = 42L;

    @Test
    public void hashCodeAndEqualsContract() {
        EqualsVerifier.forClass(TimeseriesCursor.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void encodeThenDecodeRoundTrips() {
        final TimeseriesCursor original = TimeseriesCursor.of(TIMESTAMP, REVISION);

        final TimeseriesCursor reconstructed = TimeseriesCursor.decode(original.encode());

        assertThat(reconstructed).isEqualTo(original);
        assertThat(reconstructed.getTimestamp()).isEqualTo(TIMESTAMP);
        assertThat(reconstructed.getRevision()).isEqualTo(REVISION);
    }

    @Test
    public void encodedTokenIsUrlSafeAndUnpadded() {
        final String encoded = TimeseriesCursor.of(TIMESTAMP, REVISION).encode();

        // Base64-URL alphabet only, no '+', '/', or '=' padding — safe as a bare query-string value.
        assertThat(encoded).doesNotContain("+", "/", "=");
    }

    @Test
    public void factoryRejectsNullTimestamp() {
        assertThatNullPointerException().isThrownBy(() -> TimeseriesCursor.of(null, REVISION));
    }

    @Test
    public void decodeRejectsNullInput() {
        assertThatNullPointerException().isThrownBy(() -> TimeseriesCursor.decode(null));
    }

    @Test
    public void decodeRejectsNonBase64() {
        assertThatExceptionOfType(TimeseriesQueryInvalidException.class)
                .isThrownBy(() -> TimeseriesCursor.decode("not base64 !!!"));
    }

    @Test
    public void decodeRejectsBase64OfNonJson() {
        final String garbage = base64Url("this is not json");

        assertThatExceptionOfType(TimeseriesQueryInvalidException.class)
                .isThrownBy(() -> TimeseriesCursor.decode(garbage));
    }

    @Test
    public void decodeRejectsMissingTimestampField() {
        final String noTimestamp = base64Url("{\"r\":42}");

        assertThatExceptionOfType(TimeseriesQueryInvalidException.class)
                .isThrownBy(() -> TimeseriesCursor.decode(noTimestamp));
    }

    @Test
    public void decodeRejectsMissingRevisionField() {
        final String noRevision = base64Url("{\"t\":\"2026-01-15T10:30:00Z\"}");

        assertThatExceptionOfType(TimeseriesQueryInvalidException.class)
                .isThrownBy(() -> TimeseriesCursor.decode(noRevision));
    }

    @Test
    public void decodeRejectsUnparseableTimestamp() {
        final String badTimestamp = base64Url("{\"t\":\"not-a-timestamp\",\"r\":42}");

        assertThatExceptionOfType(TimeseriesQueryInvalidException.class)
                .isThrownBy(() -> TimeseriesCursor.decode(badTimestamp));
    }

    private static String base64Url(final String raw) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
