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
package org.eclipse.ditto.base.model.headers;

import java.util.Arrays;
import java.util.Optional;

/**
 * Headers for commands and their responses which provide additional information needed for correlation and transfer.
 *
 * @since 2.3.0
 */
public enum LiveChannelTimeoutStrategy {

    /**
     * Strategy which lets the request fail with the timeout error.
     */
    FAIL("fail"),

    /**
     * Strategy which - instead of letting the timed out live request fail - will fall back to the value delivered by
     * the twin instead.
     */
    USE_TWIN("use-twin");

    private final String headerValue;

    LiveChannelTimeoutStrategy(final String headerValue) {
        this.headerValue = headerValue;
    }

    @Override
    public String toString() {
        return headerValue;
    }

    /**
     * Find a live channel timeout strategy by header value.
     *
     * @param headerValue the header value of the strategy.
     * @return the strategy with the given header value if any exists.
     */
    public static Optional<LiveChannelTimeoutStrategy> forHeaderValue(final String headerValue) {
        return Arrays.stream(values())
                .filter(strategy -> strategy.toString().equals(headerValue))
                .findAny();
    }
}
