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
package org.eclipse.ditto.timeseries.mongodb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.Test;

/**
 * Unit tests for {@link Granularity}.
 */
public final class GranularityTest {

    @Test
    public void wireFormatNamesUseLowercaseTokens() {
        assertThat(Granularity.SECONDS.getName()).isEqualTo("seconds");
        assertThat(Granularity.MINUTES.getName()).isEqualTo("minutes");
        assertThat(Granularity.HOURS.getName()).isEqualTo("hours");
    }

    @Test
    public void forNameMatchesWireFormat() {
        assertThat(Granularity.forName("seconds")).contains(Granularity.SECONDS);
        assertThat(Granularity.forName("hours")).contains(Granularity.HOURS);
    }

    @Test
    public void forNameIsCaseSensitive() {
        assertThat(Granularity.forName("SECONDS")).isEmpty();
    }

    @Test
    public void forNameReturnsEmptyForUnknownToken() {
        assertThat(Granularity.forName("days")).isEmpty();
        assertThat(Granularity.forName("")).isEmpty();
    }

    @Test
    public void forNameRejectsNullInput() {
        assertThatNullPointerException().isThrownBy(() -> Granularity.forName(null));
    }

    @Test
    public void toStringReturnsWireFormatName() {
        assertThat(Granularity.SECONDS.toString()).isEqualTo("seconds");
    }
}
