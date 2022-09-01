/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.exceptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.junit.Test;

/**
 * Unit test for {@link SignalEnrichmentFailedException}.
 */
public class SignalEnrichmentFailedExceptionTest {


    @Test
    public void assertImmutability() {
        assertInstancesOf(SignalEnrichmentFailedException.class, areImmutable());
    }

    @Test
    public void buildDefault() {
        final SignalEnrichmentFailedException underTest = SignalEnrichmentFailedException.newBuilder().build();
        assertThat(underTest.getHttpStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    public void buildForCause() {
        final DittoHeaderInvalidException cause =
                DittoHeaderInvalidException.newInvalidTypeBuilder("theHeaderName", "theValue", "theExpectedType")
                        .build();

        final SignalEnrichmentFailedException underTest = SignalEnrichmentFailedException.dueTo(cause);

        assertThat(underTest.getHttpStatus()).isEqualTo(cause.getHttpStatus());
        assertThat(underTest.getDescription().orElseThrow(() -> new AssertionError("Expect description")))
                .contains(cause.getErrorCode())
                .contains(cause.getMessage())
                .contains(cause.getDescription().orElse("-----NOT POSSIBLE-----"));
    }

    @Test
    public void testSerialization() {
        final DittoHeaderInvalidException cause =
                DittoHeaderInvalidException.newInvalidTypeBuilder("theHeaderName", "theValue", "theExpectedType")
                        .build();

        final SignalEnrichmentFailedException underTest = SignalEnrichmentFailedException.dueTo(cause);

        assertThat(SignalEnrichmentFailedException.fromJson(underTest.toJson(), underTest.getDittoHeaders()))
                .isEqualTo(underTest);
    }

}
