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
package org.eclipse.ditto.internal.utils.persistence.api.config;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Arrays;
import java.util.Optional;

/**
 * Enumeration of read concerns for mongo client.
 */
public enum ReadConcern {

    DEFAULT("default"),
    LOCAL("local"),
    MAJORITY("majority"),
    LINEARIZABLE("linearizable"),
    SNAPSHOT("snapshot"),
    AVAILABLE("available");

    private final String name;

    ReadConcern(final String name) {
        this.name = name;
    }

    /**
     * Returns the config string value of this read concern (e.g. {@code "local"}).
     *
     * @return the read concern config string.
     * @since 3.9.7
     */
    public String getName() {
        return name;
    }

    /**
     * Tries to create a ReadConcern from the given read concern string.
     *
     * @param readConcern the string value of read concern.
     * @return An optional of the ReadConcern matching to the given read preference string. Empty if no matching
     * ReadConcern exists.
     */
    public static Optional<ReadConcern> ofReadConcern(final String readConcern) {
        checkNotNull(readConcern, "readConcern");
        return Arrays.stream(values())
                .filter(c -> c.name.contentEquals(readConcern))
                .findFirst();
    }

}
