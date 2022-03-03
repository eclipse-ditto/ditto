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
 * Immutable implementation of {@link SingleAtType}.
 */
@Immutable
final class ImmutableSingleAtType implements SingleAtType {

    private final String type;

    ImmutableSingleAtType(final CharSequence type) {
        this.type = checkNotNull(type, "type").toString();
    }

    @Override
    public int length() {
        return type.length();
    }

    @Override
    public char charAt(final int index) {
        return type.charAt(index);
    }

    @Override
    public CharSequence subSequence(final int start, final int end) {
        return type.subSequence(start, end);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableSingleAtType that = (ImmutableSingleAtType) o;
        return Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type);
    }

    @Override
    public String toString() {
        return type;
    }
}
