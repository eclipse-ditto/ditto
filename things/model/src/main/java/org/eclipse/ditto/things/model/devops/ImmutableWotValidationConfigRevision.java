/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.model.devops;

import javax.annotation.concurrent.Immutable;
import java.util.Objects;

/**
 * Immutable implementation of {@link WotValidationConfigRevision}.
 * <p>
 * Represents the revision number of a WoT validation configuration.
 * </p>
 *
 * @since 3.8.0
 */
@Immutable
final class ImmutableWotValidationConfigRevision implements WotValidationConfigRevision {
    private final long value;

    ImmutableWotValidationConfigRevision(long value) {
        this.value = value;
    }

    @Override
    public long toLong() {
        return value;
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }

    @Override
    public WotValidationConfigRevision increment() {
        return new ImmutableWotValidationConfigRevision(value + 1);
    }

    @Override
    public int compareTo(WotValidationConfigRevision o) {
        return Long.compare(value, o.toLong());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ImmutableWotValidationConfigRevision that = (ImmutableWotValidationConfigRevision) o;
        return value == that.value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public boolean isGreaterThan(WotValidationConfigRevision other) {
        return this.compareTo(other) > 0;
    }

    @Override
    public boolean isGreaterThanOrEqualTo(WotValidationConfigRevision other) {
        return this.compareTo(other) >= 0;
    }

    @Override
    public boolean isLowerThan(WotValidationConfigRevision other) {
        return this.compareTo(other) < 0;
    }

    @Override
    public boolean isLowerThanOrEqualTo(WotValidationConfigRevision other) {
        return this.compareTo(other) <= 0;
    }
} 