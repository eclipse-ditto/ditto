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

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import org.junit.Test;

/**
 * Tests {@link CompletionExceptions}.
 */
public final class CompletionExceptionsTest {

    @Test
    public void nestedCompletionAndExecutionExceptionsUnwrapToTheirCause() {
        final IllegalStateException cause = new IllegalStateException("cause");
        final CompletionException wrapped = new CompletionException(new ExecutionException(cause));

        assertThat(CompletionExceptions.unwrap(wrapped)).isSameAs(cause);
    }

    @Test
    public void executionExceptionUnwrapsToItsCause() {
        final IllegalStateException cause = new IllegalStateException("cause");

        assertThat(CompletionExceptions.unwrap(new ExecutionException(cause))).isSameAs(cause);
    }

    @Test
    public void completionExceptionWithoutCauseIsReturnedItself() {
        final CompletionException noCause = new CompletionException(null);

        assertThat(CompletionExceptions.unwrap(noCause)).isSameAs(noCause);
    }

    @Test
    public void executionWrappingACauselessCompletionExceptionStopsThere() {
        final CompletionException causeless = new CompletionException(null);

        assertThat(CompletionExceptions.unwrap(new ExecutionException(causeless))).isSameAs(causeless);
    }

    @Test
    public void plainExceptionIsReturnedItself() {
        final RuntimeException plain = new RuntimeException("plain");
        final RuntimeException withCause = new RuntimeException("with cause", new IllegalStateException("cause"));

        assertThat(CompletionExceptions.unwrap(plain)).isSameAs(plain);
        assertThat(CompletionExceptions.unwrap(withCause)).isSameAs(withCause);
    }

}
