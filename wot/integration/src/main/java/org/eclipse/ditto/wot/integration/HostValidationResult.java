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
package org.eclipse.ditto.wot.integration;

import javax.annotation.Nullable;

/**
 * Holds the result of validating a host against the WoT ThingModel fetch host-validation configuration.
 */
final class HostValidationResult {

    private final boolean valid;
    @Nullable private final String host;
    @Nullable private final String reason;

    private HostValidationResult(final boolean valid, @Nullable final String host, @Nullable final String reason) {
        this.valid = valid;
        this.host = host;
        this.reason = reason;
    }

    /**
     * @return a valid {@link HostValidationResult}.
     */
    static HostValidationResult valid() {
        return new HostValidationResult(true, null, null);
    }

    /**
     * @param host the invalid host.
     * @param reason why the host is invalid.
     * @return the {@link HostValidationResult} for the invalid host.
     */
    static HostValidationResult invalid(final String host, final String reason) {
        return new HostValidationResult(false, host, reason);
    }

    /**
     * @param host the blocked host.
     * @param reason why the host is blocked.
     * @return the {@link HostValidationResult} for the blocked host.
     */
    static HostValidationResult blocked(final String host, final String reason) {
        return new HostValidationResult(false, host, reason);
    }

    /**
     * @return whether the host is valid (i.e. allowed to be fetched from).
     */
    boolean isValid() {
        return valid;
    }

    /**
     * @return the host this result refers to, or {@code null} if the host was valid.
     */
    @Nullable
    String getHost() {
        return host;
    }

    /**
     * @return the reason why the host was blocked/invalid, or {@code null} if the host was valid.
     */
    @Nullable
    String getReason() {
        return reason;
    }

}
