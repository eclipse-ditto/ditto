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
package org.eclipse.ditto.internal.utils.config;

/**
 * This error indicates a problem with the service configuration.
 */
public class DittoConfigError extends Error {

    private static final long serialVersionUID = -2090250370652854565L;

    /**
     * Constructs a new {@code DittoConfigError} object.
     *
     * @param message the detail message of the error.
     */
    public DittoConfigError(final String message) {
        super(message);
    }

    /**
     * Constructs a new {@code DittoConfigError} object.
     *
     * @param cause the cause of the error.
     */
    public DittoConfigError(final Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new {@code DittoConfigError} object.
     *
     * @param message the detail message of the error.
     * @param cause the cause of the error.
     */
    public DittoConfigError(final String message, final Throwable cause) {
        super(message, cause);
    }

}
