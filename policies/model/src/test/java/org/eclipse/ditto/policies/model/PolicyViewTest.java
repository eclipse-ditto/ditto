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
package org.eclipse.ditto.policies.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.Optional;

import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.junit.Test;

/**
 * Unit test for {@link PolicyView}.
 */
public final class PolicyViewTest {

    @Test
    public void parsesOriginal() {
        assertThat(PolicyView.fromString("original")).contains(PolicyView.ORIGINAL);
    }

    @Test
    public void parsesResolved() {
        assertThat(PolicyView.fromString("resolved")).contains(PolicyView.RESOLVED);
    }

    @Test
    public void parsesMixedCase() {
        assertThat(PolicyView.fromString("RESOLVED")).contains(PolicyView.RESOLVED);
        assertThat(PolicyView.fromString("Resolved")).contains(PolicyView.RESOLVED);
    }

    @Test
    public void trimsWhitespace() {
        assertThat(PolicyView.fromString("  resolved  ")).contains(PolicyView.RESOLVED);
        assertThat(PolicyView.fromString("\tresolved\n")).contains(PolicyView.RESOLVED);
    }

    @Test
    public void emptyForNull() {
        assertThat(PolicyView.fromString(null)).isEmpty();
    }

    @Test
    public void emptyForBlank() {
        assertThat(PolicyView.fromString("")).isEmpty();
        assertThat(PolicyView.fromString("   ")).isEmpty();
    }

    @Test
    public void unknownValueThrows() {
        assertThatExceptionOfType(PolicyViewInvalidException.class)
                .isThrownBy(() -> PolicyView.fromString("merged"))
                .withMessageContaining("merged")
                .withMessageContaining("original")
                .withMessageContaining("resolved");
    }

    @Test
    public void effectiveIsNoLongerAccepted() {
        assertThatExceptionOfType(PolicyViewInvalidException.class)
                .isThrownBy(() -> PolicyView.fromString("effective"));
    }

    @Test
    public void isResolvedTrueOnlyForResolved() {
        assertThat(PolicyView.ORIGINAL.isResolved()).isFalse();
        assertThat(PolicyView.RESOLVED.isResolved()).isTrue();
    }

    @Test
    public void getValueReturnsLowercaseWireValue() {
        assertThat(PolicyView.ORIGINAL.getValue()).isEqualTo("original");
        assertThat(PolicyView.RESOLVED.getValue()).isEqualTo("resolved");
        assertThat(PolicyView.RESOLVED.toString()).isEqualTo("resolved");
    }

    @Test
    public void fromHeadersReadsCorrectKey() {
        final DittoHeaders headers = DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.POLICY_VIEW.getKey(), "resolved")
                .build();
        assertThat(PolicyView.from(headers)).contains(PolicyView.RESOLVED);
    }

    @Test
    public void fromHeadersEmptyWhenAbsent() {
        final Optional<PolicyView> v = PolicyView.from(DittoHeaders.empty());
        assertThat(v).isEmpty();
    }

    @Test
    public void fromHeadersThrowsOnInvalid() {
        final DittoHeaders headers = DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.POLICY_VIEW.getKey(), "bogus")
                .build();
        assertThatExceptionOfType(PolicyViewInvalidException.class)
                .isThrownBy(() -> PolicyView.from(headers));
    }

}
