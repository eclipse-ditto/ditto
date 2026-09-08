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
 * Unit tests for {@link Ingest}.
 */
public final class IngestTest {

    @Test
    public void wireFormatNamesUseUppercaseTokens() {
        assertThat(Ingest.ALL.getName()).isEqualTo("ALL");
        assertThat(Ingest.NONE.getName()).isEqualTo("NONE");
    }

    @Test
    public void forNameMatchesWireFormat() {
        assertThat(Ingest.forName("ALL")).contains(Ingest.ALL);
        assertThat(Ingest.forName("NONE")).contains(Ingest.NONE);
    }

    @Test
    public void forNameIsCaseSensitive() {
        assertThat(Ingest.forName("all")).isEmpty();
        assertThat(Ingest.forName("All")).isEmpty();
    }

    @Test
    public void forNameReturnsEmptyForUnknownToken() {
        assertThat(Ingest.forName("CONDITIONAL")).isEmpty();
        assertThat(Ingest.forName("")).isEmpty();
    }

    @Test
    public void forNameRejectsNullInput() {
        assertThatNullPointerException().isThrownBy(() -> Ingest.forName(null));
    }

    @Test
    public void toStringReturnsWireFormatName() {
        assertThat(Ingest.ALL.toString()).isEqualTo("ALL");
    }

    @Test
    public void charSequenceContractDelegatesToName() {
        assertThat(Ingest.ALL.length()).isEqualTo(3);
        assertThat(Ingest.NONE.charAt(0)).isEqualTo('N');
        assertThat(Ingest.NONE.subSequence(0, 2)).isEqualTo("NO");
    }
}
