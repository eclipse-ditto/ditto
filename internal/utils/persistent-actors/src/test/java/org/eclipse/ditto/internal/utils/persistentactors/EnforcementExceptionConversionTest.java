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
package org.eclipse.ditto.internal.utils.persistentactors;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletionException;

import org.apache.pekko.pattern.AskTimeoutException;
import org.eclipse.ditto.base.model.exceptions.DittoInternalErrorException;
import org.eclipse.ditto.base.model.exceptions.EnforcementTimeoutException;
import org.junit.Test;

/**
 * Tests the enforcement exception conversion logic in {@link AbstractPersistenceSupervisor}, specifically the
 * conversion of {@link AskTimeoutException} to {@link EnforcementTimeoutException} (503 instead of 500).
 */
public final class EnforcementExceptionConversionTest {

    @Test
    public void unwrapDirectAskTimeoutException() {
        final Throwable original = new AskTimeoutException("enforcer timed out");
        final Throwable unwrapped = AbstractPersistenceSupervisor.unwrapCompletionException(original);
        assertThat(unwrapped).isSameAs(original);
        assertThat(unwrapped).isInstanceOf(AskTimeoutException.class);
    }

    @Test
    public void unwrapSingleCompletionException() {
        final AskTimeoutException cause = new AskTimeoutException("enforcer timed out");
        final CompletionException wrapped = new CompletionException(cause);
        final Throwable unwrapped = AbstractPersistenceSupervisor.unwrapCompletionException(wrapped);
        assertThat(unwrapped).isSameAs(cause);
        assertThat(unwrapped).isInstanceOf(AskTimeoutException.class);
    }

    @Test
    public void unwrapDoubleCompletionException() {
        final AskTimeoutException cause = new AskTimeoutException("enforcer timed out");
        final CompletionException inner = new CompletionException(cause);
        final CompletionException outer = new CompletionException(inner);
        final Throwable unwrapped = AbstractPersistenceSupervisor.unwrapCompletionException(outer);
        assertThat(unwrapped).isSameAs(cause);
        assertThat(unwrapped).isInstanceOf(AskTimeoutException.class);
    }

    @Test
    public void unwrapCompletionExceptionWithNullCauseReturnsItself() {
        final CompletionException noCause = new CompletionException(null);
        final Throwable unwrapped = AbstractPersistenceSupervisor.unwrapCompletionException(noCause);
        assertThat(unwrapped).isSameAs(noCause);
    }

    @Test
    public void unwrapNonCompletionExceptionReturnsSame() {
        final RuntimeException other = new RuntimeException("other");
        final Throwable unwrapped = AbstractPersistenceSupervisor.unwrapCompletionException(other);
        assertThat(unwrapped).isSameAs(other);
    }

    @Test
    public void unwrappedAskTimeoutIsNotDittoRuntimeException() {
        final AskTimeoutException cause = new AskTimeoutException("enforcer timed out");
        final CompletionException wrapped = new CompletionException(cause);
        final Throwable unwrapped = AbstractPersistenceSupervisor.unwrapCompletionException(wrapped);

        // AskTimeoutException is NOT a DittoRuntimeException — this is the key distinction
        // that makes it fall through to the EnforcementTimeoutException branch
        assertThat(unwrapped).isNotInstanceOf(
                org.eclipse.ditto.base.model.exceptions.DittoRuntimeException.class);
        assertThat(unwrapped).isInstanceOf(AskTimeoutException.class);
    }

    @Test
    public void enforcementTimeoutExceptionHasCorrectProperties() {
        final EnforcementTimeoutException exception = EnforcementTimeoutException.newBuilder().build();
        assertThat(exception.getHttpStatus().getCode()).isEqualTo(503);
        assertThat(exception.getErrorCode()).isEqualTo("enforcement.timeout");
        assertThat(exception).isNotInstanceOf(DittoInternalErrorException.class);
    }
}
