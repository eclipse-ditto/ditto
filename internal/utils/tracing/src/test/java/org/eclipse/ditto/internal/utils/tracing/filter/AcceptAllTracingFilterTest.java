/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.tracing.filter;

import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.internal.utils.tracing.span.SpanOperationName;
import org.junit.Test;

/**
 * Unit test for {@link AcceptAllTracingFilter}.
 */
public final class AcceptAllTracingFilterTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(AcceptAllTracingFilter.class, areImmutable());
    }

    @Test
    public void acceptReturnsTrue() {
        final var underTest = AcceptAllTracingFilter.getInstance();

        Assertions.assertThat(underTest.accept(SpanOperationName.of("/api/2/things/x"))).isTrue();
    }

}