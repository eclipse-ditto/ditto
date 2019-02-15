/*
* Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.base.config;

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
