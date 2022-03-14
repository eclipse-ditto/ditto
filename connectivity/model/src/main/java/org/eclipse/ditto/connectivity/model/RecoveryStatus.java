/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.model;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Arrays;
import java.util.Optional;

/**
 * An enumeration of status regarding recovery of a connection.
 *
 * @since 2.4.0
 */
public enum RecoveryStatus implements CharSequence {

    /**
     * Recovery status indicating that the recovery of a client is still ongoing, e.g. retrying because of
     * misconfiguration/failures.
     */
    ONGOING("ongoing"),

    /**
     * Recovery status indicating that the recovery of a client succeeded and that it could connect.
     */
    SUCCEEDED("succeeded"),

    /**
     * Recovery status indicating that the recovery was stopped because the max. backoff limit regarding times and/or
     * amount of retries was reached.
     */
    BACK_OFF_LIMIT_REACHED("backOffLimitReached"),

    /**
     * Unknown recovery status which e.g. is the initial recovery status.
     */
    UNKNOWN("unknown");

    private final String name;

    RecoveryStatus(final String name) {
        this.name = checkNotNull(name);
    }

    /**
     * Returns the {@code RecoveryStatus} for the given {@code name} if it exists.
     *
     * @param name the name.
     * @return the RecoveryStatus or an empty optional.
     */
    public static Optional<RecoveryStatus> forName(final CharSequence name) {
        checkNotNull(name, "name");
        return Arrays.stream(values())
                .filter(c -> c.name.contentEquals(name))
                .findFirst();
    }

    /**
     * Returns the name of this {@code RecoveryStatus}.
     *
     * @return the name.
     */
    public String getName() {
        return name;
    }

    @Override
    public int length() {
        return name.length();
    }

    @Override
    public char charAt(final int index) {
        return name.charAt(index);
    }

    @Override
    public CharSequence subSequence(final int start, final int end) {
        return name.subSequence(start, end);
    }

    @Override
    public String toString() {
        return name;
    }

}
