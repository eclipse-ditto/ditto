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

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/**
 * Unwraps the exceptions with which futures and completion stages wrap the error they failed with.
 *
 * @since 4.0.0
 */
public final class CompletionExceptions {

    private CompletionExceptions() {
        throw new AssertionError();
    }

    /**
     * Follows the causes of {@link CompletionException}s and {@link ExecutionException}s.
     * <p>
     * While the current throwable is a {@code CompletionException} or an {@code ExecutionException} with a non-null
     * cause, its cause becomes the current throwable. The causes of any other throwable are not followed.
     * </p>
     *
     * @param throwable the throwable to unwrap; must not be {@code null}.
     * @return the first throwable that is neither a {@code CompletionException} nor an {@code ExecutionException}, or
     * such a wrapper that has no cause.
     */
    public static Throwable unwrap(final Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

}
