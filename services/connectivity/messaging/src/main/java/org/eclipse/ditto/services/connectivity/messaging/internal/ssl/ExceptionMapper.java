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
package org.eclipse.ditto.services.connectivity.messaging.internal.ssl;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionUnavailableException;

/**
 * Helper class to map SSL exceptions to Ditto exceptions with more meaningful descriptions.
 */
final class ExceptionMapper {

    private final DittoHeaders dittoHeaders;

    /**
     * Instantiates a new {@link ExceptionMapper}.
     *
     * @param dittoHeaders ditto headers
     */
    ExceptionMapper(@Nullable final DittoHeaders dittoHeaders) {
        this.dittoHeaders = dittoHeaders != null ? dittoHeaders : DittoHeaders.empty();
    }

    /**
     * @return preconfigured builder for fatal errors
     */
    DittoRuntimeExceptionBuilder<ConnectionUnavailableException> fatalError(final String whatHappened) {
        return ConnectionUnavailableException.newBuilder(ConnectionId.of("unimportant"))
                .message(String.format("Fatal error: %s.", whatHappened))
                .description("Please contact the service team.")
                .dittoHeaders(dittoHeaders);
    }

    /**
     * @return preconfigured builder for bad format errors
     */
    DittoRuntimeExceptionBuilder<ConnectionConfigurationInvalidException> badFormat(final JsonPointer errorLocation,
            final String label, final String binaryFormat) {
        final String message = String.format("%s: bad format. " +
                        "Expect PEM-encoded %s data specified by RFC-7468 starting with '-----BEGIN %s-----'",
                errorLocation.toString(), binaryFormat, label);
        return ConnectionConfigurationInvalidException.newBuilder(message)
                .dittoHeaders(dittoHeaders);
    }

}
