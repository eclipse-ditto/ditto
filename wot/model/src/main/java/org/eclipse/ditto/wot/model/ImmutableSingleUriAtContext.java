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
 * Immutable implementation of {@link SingleUriAtContext}.
 */
@Immutable
final class ImmutableSingleUriAtContext implements SingleUriAtContext {

    private final String context;

    ImmutableSingleUriAtContext(final CharSequence context) {
        this.context = checkNotNull(context, "context").toString();
    }

    @Override
    public int length() {
        return context.length();
    }

    @Override
    public char charAt(final int index) {
        return context.charAt(index);
    }

    @Override
    public CharSequence subSequence(final int start, final int end) {
        return context.subSequence(start, end);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableSingleUriAtContext that = (ImmutableSingleUriAtContext) o;
        return Objects.equals(context, that.context);
    }

    @Override
    public int hashCode() {
        return Objects.hash(context);
    }

    @Override
    public String toString() {
        return context;
    }
}
