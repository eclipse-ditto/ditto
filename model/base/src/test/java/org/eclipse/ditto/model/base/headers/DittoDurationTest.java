/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.model.base.headers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Duration;

import org.junit.Test;

/**
 * Tests {@link org.eclipse.ditto.model.base.headers.DittoDuration}.
 */
public final class DittoDurationTest {

    @Test
    public void createDittoDurationFromString() {
        final DittoDuration dittoDuration = DittoDuration.fromTimeoutString("42");
        assertThat(dittoDuration.getDuration()).isEqualByComparingTo(Duration.ofSeconds(42));
    }

    @Test
    public void createDittoDurationFromStringSeconds() {
        final DittoDuration dittoDuration = DittoDuration.fromTimeoutString("53s");
        assertThat(dittoDuration.getDuration()).isEqualByComparingTo(Duration.ofSeconds(53));
    }

    @Test
    public void createDittoDurationFromStringMilliseconds() {
        final DittoDuration dittoDuration = DittoDuration.fromTimeoutString("763ms");
        assertThat(dittoDuration.getDuration()).isEqualByComparingTo(Duration.ofMillis(763));
    }

    @Test
    public void createDittoDurationFromStringWithAndWithoutSecondsIsEqual() {
        final DittoDuration dittoDuration1 = DittoDuration.fromTimeoutString("23");
        final DittoDuration dittoDuration2 = DittoDuration.fromTimeoutString("23s");
        assertThat(dittoDuration1.getDuration()).isEqualByComparingTo(dittoDuration2.getDuration());
    }

    @Test
    public void createDittoDurationFromStringMinutesFails() {
        assertThatExceptionOfType(NumberFormatException.class)
                .isThrownBy(() -> DittoDuration.fromTimeoutString("5m"));
    }

    @Test
    public void createDittoDurationFromStringHoursFails() {
        assertThatExceptionOfType(NumberFormatException.class)
                .isThrownBy(() -> DittoDuration.fromTimeoutString("1h"));
    }
}