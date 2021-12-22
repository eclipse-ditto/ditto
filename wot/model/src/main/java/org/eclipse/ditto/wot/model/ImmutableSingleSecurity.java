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
package org.eclipse.ditto.wot.model;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Immutable implementation of {@link SingleSecurity}.
 */
@Immutable
final class ImmutableSingleSecurity implements SingleSecurity {

    private final String security;

    ImmutableSingleSecurity(final CharSequence security) {
        this.security = checkNotNull(security, "security").toString();
    }

    @Override
    public int length() {
        return security.length();
    }

    @Override
    public char charAt(final int index) {
        return security.charAt(index);
    }

    @Override
    public CharSequence subSequence(final int start, final int end) {
        return security.subSequence(start, end);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableSingleSecurity that = (ImmutableSingleSecurity) o;
        return Objects.equals(security, that.security);
    }

    @Override
    public int hashCode() {
        return Objects.hash(security);
    }

    @Override
    public String toString() {
        return security;
    }
}
