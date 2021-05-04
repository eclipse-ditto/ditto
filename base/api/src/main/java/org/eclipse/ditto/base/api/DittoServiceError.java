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
package org.eclipse.ditto.base.api;

/**
 * A {@code DittoServiceError} indicates a serious problem of a Ditto service that should not tried to be caught as
 * there is no way to reasonably deal with the error or the resume the service without fixing the root cause of the
 * error.
 */
public class DittoServiceError extends Error {

    private static final long serialVersionUID = -2164259929709871859L;

    /**
     * Constructs a new error with the specified detail message but without a cause.
     *
     * @param message the detail message.
     */
    public DittoServiceError(final String message) {
        super(message);
    }

    /**
     * Constructs a new error with the specified cause but without a detail message.
     *
     * @param cause the cause.
     */
    public DittoServiceError(final Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new error with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause.
     */
    public DittoServiceError(final String message, final Throwable cause) {
        super(message, cause);
    }

}
