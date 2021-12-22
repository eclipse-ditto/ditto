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
package org.eclipse.ditto.placeholders;

/**
 * A {@link Placeholder} that resolves time placeholders, e.g. from the current time.
 *
 * @since 2.3.0
 */
public interface TimePlaceholder extends Placeholder<Object> {

    /**
     * The used prefix of this placeholder.
     */
    String PREFIX = "time";

    /**
     * Returns the singleton instance of the {@link TimePlaceholder}.
     *
     * @return the singleton instance.
     */
    static TimePlaceholder getInstance() {
        return ImmutableTimePlaceholder.INSTANCE;
    }
}
