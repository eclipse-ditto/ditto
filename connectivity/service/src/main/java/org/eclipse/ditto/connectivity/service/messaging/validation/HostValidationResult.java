/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.validation;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.ConnectionConfigurationInvalidException;

/**
 * Holds the result of hostname validation and provides a method to create an appropriate
 * {@link org.eclipse.ditto.connectivity.model.ConnectionConfigurationInvalidException}.
 */
final class HostValidationResult {

    private final boolean valid;
    @Nullable private final String host;
    @Nullable private final String message;

    private HostValidationResult(final boolean valid, @Nullable final String host, @Nullable final String message) {
        this.valid = valid;
        this.host = host;
        this.message = message;
    }

    /**
     * @return a valid {@link HostValidationResult}
     */
    static HostValidationResult valid() {
        return new HostValidationResult(true, null, null);
    }

    /**
     * @param host the invalid host
     * @param reason why the host is invalid
     * @return the {@link HostValidationResult} for the invalid host
     */
    static HostValidationResult invalid(final String host, final String reason) {
        final var errorMessage = String.format("The configured host '%s' is invalid: %s", host, reason);
        return new HostValidationResult(false, host, errorMessage);
    }

    /**
     * @param host the blocked host
     * @param reason why the host is blocked
     * @return the {@link HostValidationResult} for the blocked host
     */
    static HostValidationResult blocked(final String host, final String reason) {
        final var exceptionMessage = String.format("the host is blocked: %s", reason);
        return new HostValidationResult(false, host, exceptionMessage);
    }

    /**
     * @param host the blocked host
     * @return the {@link HostValidationResult} for the blocked host
     */
    static HostValidationResult blocked(final String host) {
        return blocked(host, "the host is blocked.");
    }

    /**
     * @return whether the host is valid
     */
    boolean isValid() {
        return valid;
    }

    /**
     * Creates a {@link org.eclipse.ditto.connectivity.model.ConnectionConfigurationInvalidException} with meaningful message and description.
     *
     * @param dittoHeaders the headers of the request
     * @return the appropriate {@link org.eclipse.ditto.connectivity.model.ConnectionConfigurationInvalidException}
     */
    ConnectionConfigurationInvalidException toException(final DittoHeaders dittoHeaders) {
        final var errorMessage = String.format("The configured host '%s' may not be used for the " +
                "connection because %s", host, message);
        return ConnectionConfigurationInvalidException.newBuilder(errorMessage)
                .description("It is a blocked or otherwise forbidden hostname which may not be used.")
                .dittoHeaders(dittoHeaders)
                .build();
    }

}
