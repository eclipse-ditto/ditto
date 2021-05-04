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

package org.eclipse.ditto.connectivity.service.messaging.monitoring.logs;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * Class that helps with connectivity related headers.
 */
final class ConnectivityHeaders {

    private static final String CONNECTIVITY_DEBUG_LOG_HEADER = "connectivity-debug-log";

    /**
     * Check if the header values may be logged in the connectivity logs.
     * @param headers the headers of the message that might want to log its headers.
     * @return {@code true} if the header values of the message may be logged.
     */
    static boolean isHeadersDebugLogEnabled(final Map<String, String> headers) {
        final String value = getDebugLogValue(headers);
        return ConnectivityDebugLogValues.HEADER.allowedForExternalHeaderValue(value);
    }

    /**
     * Check if the payload may be logged in the connectivity logs.
     * @param headers the headers of the message that might want to log its payload.
     * @return {@code true} if the payload of the message may be logged.
     */
    static boolean isPayloadDebugLogEnabled(final Map<String, String> headers) {
        final String value = getDebugLogValue(headers);
        return ConnectivityDebugLogValues.PAYLOAD.allowedForExternalHeaderValue(value);
    }

    @Nullable
    private static String getDebugLogValue(final Map<String, String> headers) {
        return headers.getOrDefault(CONNECTIVITY_DEBUG_LOG_HEADER, "ALL");
    }

    private enum ConnectivityDebugLogValues {
        HEADER("HEADER", "ALL"),
        PAYLOAD("PAYLOAD", "ALL");

        private final List<String> allowedValues;

        ConnectivityDebugLogValues(final String... values) {
            this.allowedValues = Collections.unmodifiableList(Arrays.asList(values));
        }

        boolean allowedForExternalHeaderValue(@Nullable final String externalHeaderValue) {
            return this.allowedValues.contains(externalHeaderValue);
        }

    }

}
