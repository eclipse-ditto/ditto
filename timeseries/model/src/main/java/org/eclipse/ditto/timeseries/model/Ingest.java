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
package org.eclipse.ditto.timeseries.model;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Arrays;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

/**
 * Enumeration of ingestion modes declared on a WoT property via {@code ditto:timeseries.ingest}.
 *
 * @since 4.0.0
 */
@Immutable
public enum Ingest implements CharSequence {

    /**
     * Ingest every value change for this property into the timeseries database.
     */
    ALL("ALL"),

    /**
     * Disable ingestion for this property. Useful for temporarily pausing collection without
     * removing the {@code ditto:timeseries} declaration entirely.
     */
    NONE("NONE");

    private final String name;

    Ingest(final String name) {
        this.name = name;
    }

    /**
     * @return the wire-format name of this ingestion mode (uppercase token).
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the {@code Ingest} for the given wire-format name.
     *
     * @param name the wire-format name (case-sensitive).
     * @return the matching value or empty if unknown.
     * @throws NullPointerException if {@code name} is {@code null}.
     */
    public static Optional<Ingest> forName(final CharSequence name) {
        checkNotNull(name, "name");
        return Arrays.stream(values()).filter(i -> i.name.contentEquals(name)).findFirst();
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
