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
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.Test;

/**
 * Unit tests for {@link SortOrder}.
 */
public final class SortOrderTest {

    @Test
    public void wireFormatNamesUseLowercaseTokens() {
        assertThat(SortOrder.ASC.getName()).isEqualTo("asc");
        assertThat(SortOrder.DESC.getName()).isEqualTo("desc");
    }

    @Test
    public void toStringReturnsWireFormatName() {
        assertThat(SortOrder.DESC.toString()).isEqualTo("desc");
    }

    @Test
    public void forNameMatchesWireFormat() {
        assertThat(SortOrder.forName("asc")).contains(SortOrder.ASC);
        assertThat(SortOrder.forName("desc")).contains(SortOrder.DESC);
    }

    @Test
    public void forNameIsCaseSensitive() {
        assertThat(SortOrder.forName("ASC")).isEmpty();
        assertThat(SortOrder.forName("Desc")).isEmpty();
    }

    @Test
    public void forNameReturnsEmptyForUnknownToken() {
        assertThat(SortOrder.forName("ascending")).isEmpty();
        assertThat(SortOrder.forName("")).isEmpty();
    }

    @Test
    public void forNameRejectsNullInput() {
        assertThatNullPointerException().isThrownBy(() -> SortOrder.forName(null));
    }

    @Test
    public void everyEnumValueResolvesViaForName() {
        for (final SortOrder order : SortOrder.values()) {
            assertThat(SortOrder.forName(order.getName())).contains(order);
        }
    }
}
