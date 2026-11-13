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
package org.eclipse.ditto.internal.utils.persistence.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.Test;

/**
 * Tests {@link ReadJournalRestarts}.
 */
public final class ReadJournalRestartsTest {

    @Test
    public void backOffValues() {
        assertThat(ReadJournalRestarts.MIN_BACKOFF).isEqualTo(Duration.ofSeconds(1));
        assertThat(ReadJournalRestarts.MAX_BACKOFF).isEqualTo(Duration.ofSeconds(128));
        assertThat(ReadJournalRestarts.RANDOM_FACTOR).isEqualTo(0.1);
    }

    @Test
    public void restartsGrowWithTheBinaryLogarithmOfMaxIdleTimeUpToTheMaximumBackOff() {
        assertThat(ReadJournalRestarts.computeMaxRestarts(Duration.ZERO)).isZero();
        assertThat(ReadJournalRestarts.computeMaxRestarts(Duration.ofSeconds(1))).isZero();
        assertThat(ReadJournalRestarts.computeMaxRestarts(Duration.ofSeconds(2))).isEqualTo(1);
        assertThat(ReadJournalRestarts.computeMaxRestarts(Duration.ofSeconds(60))).isEqualTo(5);
        assertThat(ReadJournalRestarts.computeMaxRestarts(Duration.ofSeconds(128))).isEqualTo(7);
    }

    @Test
    public void beyondTheMaximumBackOffAtLeastSevenRestartsPlusOnePerMaximumBackOff() {
        assertThat(ReadJournalRestarts.computeMaxRestarts(Duration.ofSeconds(129))).isEqualTo(7);
        // the shipped persistence ping interval (10m) is the maxIdleTime of the ping streams.
        assertThat(ReadJournalRestarts.computeMaxRestarts(Duration.ofMinutes(10))).isEqualTo(10);
        assertThat(ReadJournalRestarts.computeMaxRestarts(Duration.ofHours(1))).isEqualTo(34);
    }

}
