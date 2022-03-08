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
 * Immutable implementation of {@link SingleOAuth2Scopes}.
 */
@Immutable
final class ImmutableSingleOAuth2Scopes implements SingleOAuth2Scopes {

    private final String scopes;

    ImmutableSingleOAuth2Scopes(final CharSequence scopes) {
        this.scopes = checkNotNull(scopes, "scopes").toString();
    }

    @Override
    public int length() {
        return scopes.length();
    }

    @Override
    public char charAt(final int index) {
        return scopes.charAt(index);
    }

    @Override
    public CharSequence subSequence(final int start, final int end) {
        return scopes.subSequence(start, end);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableSingleOAuth2Scopes that = (ImmutableSingleOAuth2Scopes) o;
        return Objects.equals(scopes, that.scopes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scopes);
    }

    @Override
    public String toString() {
        return scopes;
    }
}
