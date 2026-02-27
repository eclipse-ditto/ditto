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
package org.eclipse.ditto.policies.model;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Arrays;
import java.util.Optional;

/**
 * Determines which types of additions are allowed when a policy entry is imported by other policies
 * via {@code entriesAdditions}.
 *
 * @since 3.9.0
 */
public enum AllowedImportAddition {

    /**
     * Allows importing policies to add additional subjects to this entry.
     */
    SUBJECTS("subjects"),

    /**
     * Allows importing policies to add additional resources to this entry.
     */
    RESOURCES("resources");

    private final String name;

    AllowedImportAddition(final String name) {
        this.name = name;
    }

    /**
     * Returns the name of this allowed import addition type as used in JSON serialization.
     *
     * @return the name.
     */
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }

    /**
     * Returns the {@code AllowedImportAddition} matching the given name.
     *
     * @param name the name to look up.
     * @return the matching {@code AllowedImportAddition}, or empty if no match.
     * @throws NullPointerException if {@code name} is {@code null}.
     */
    public static Optional<AllowedImportAddition> forName(final CharSequence name) {
        checkNotNull(name, "name");
        return Arrays.stream(values())
                .filter(c -> c.name.contentEquals(name))
                .findFirst();
    }

}
