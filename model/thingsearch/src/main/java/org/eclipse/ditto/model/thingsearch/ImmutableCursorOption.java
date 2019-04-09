/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.model.thingsearch;

import javax.annotation.concurrent.Immutable;

// TODO: test
@Immutable
final class ImmutableCursorOption implements CursorOption {

    private final String cursor;

    private ImmutableCursorOption(final String cursor) {
        this.cursor = cursor;
    }

    /**
     * Returns a new instance of {@code ImmutableCursorOption} with the given size.
     *
     * @param size the page size.
     * @return the new size option.
     */
    static ImmutableCursorOption of(final String size) {
        return new ImmutableCursorOption(size);
    }

    @Override
    public void accept(final OptionVisitor visitor) {
        throw new IllegalStateException("TODO: replace this method");
    }

    @Override
    public boolean equals(final Object o) {
        if (o instanceof ImmutableCursorOption) {
            final ImmutableCursorOption that = (ImmutableCursorOption) o;
            return cursor == that.cursor;
        } else {
            return false;
        }
    }

    @Override
    public String getCursor() {
        return cursor;
    }

    @Override
    public int hashCode() {
        return cursor.hashCode();
    }

    @Override
    public String toString() {
        return "cursor(" + cursor + ")";
    }
}
