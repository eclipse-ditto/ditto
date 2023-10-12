/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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
 * Possible options for Ditto's {@code if-equal} header.
 *
 * @since 3.3.0
 */
public enum IfEqual {

    /**
     * Option which updates a value, even if the value is the same (via {@code equal()}) than the value before.
     * This is the default if omitted and for backwards compatibility.
     */
    UPDATE("update"),

    /**
     * Option which will skip the update of a twin if the new value is the same (via {@code equal()}) than the value
     * before.
     */
    SKIP("skip"),

    /**
     * Option which will skip the update of a twin if the new value is the same (via {@code equal()}) than the value
     * before.
     * And additionally minimizes a "Merge" command to only the actually changed fields compared to the current state
     * of the entity. This can be beneficial to reduce (persisted and emitted) events to the minimum of what actually
     * did change.
     *
     * @since 3.4.0
     */
    SKIP_MINIMIZING_MERGE("skip-minimizing-merge");

    private final String option;

    IfEqual(final String option) {
        this.option = option;
    }

    @Override
    public String toString() {
        return option;
    }

    /**
     * Find an If-Equal option by a provided option string.
     *
     * @param option the option.
     * @return the option with the given option string if any exists.
     */
    public static Optional<IfEqual> forOption(final String option) {
        return Arrays.stream(values())
                .filter(strategy -> strategy.toString().equals(option))
                .findAny();
    }
}
