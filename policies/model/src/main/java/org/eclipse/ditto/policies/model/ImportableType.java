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
package org.eclipse.ditto.policies.model;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Arrays;
import java.util.Optional;

/**
 * Determines whether and how a policy entry can be imported by other policies.
 *
 * @since 3.1.0
 */
public enum ImportableType {

    /**
     * The entry is imported in the target policy without being listed there individually.
     */
    IMPLICIT("implicit"),
    /**
     * The entry is imported only if it is listed in the target policy.
     */
    EXPLICIT("explicit"),
    /**
     * The entry is never imported, it is only effective in the own policy itself.
     */
    NEVER("never");

    private final String name;

    ImportableType(final String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }

    /**
     * @param name importable type as string
     * @return the ImportableType matching the given name
     */
    public static Optional<ImportableType> forName(final CharSequence name) {
        checkNotNull(name, "name");
        return Arrays.stream(values())
                .filter(c -> c.name.contentEquals(name))
                .findFirst();
    }

}
