/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

import org.eclipse.ditto.base.model.headers.DittoHeaders;

/**
 * Validates the given host.
 */
@FunctionalInterface
interface HostValidator {

    /**
     * Validate the host.
     *
     * @param host the host to validate
     * @return the HostValidationResult
     */
    HostValidationResult validateHost(String host);

    /**
     * Validates the given host.
     *
     * @throws org.eclipse.ditto.connectivity.model.ConnectionConfigurationInvalidException if the connection is not valid
     */
    default void validateHostname(final String host, final DittoHeaders dittoHeaders) {
        final HostValidationResult validationResult = validateHost(host);
        if (!validationResult.isValid()) {
            throw validationResult.toException(dittoHeaders);
        }
    }
}
