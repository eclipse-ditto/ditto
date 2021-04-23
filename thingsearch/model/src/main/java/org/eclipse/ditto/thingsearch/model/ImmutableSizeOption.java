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
package org.eclipse.ditto.thingsearch.model;

import javax.annotation.concurrent.Immutable;

@Immutable
final class ImmutableSizeOption implements SizeOption {

    /**
     * The maximum allowed page size.
     */
    static final int MAX_SIZE = 200;

    private final int size;

    private ImmutableSizeOption(final int size) {
        this.size = size;
    }

    /**
     * Returns a new instance of {@code ImmutableSizeOption} with the given size.
     *
     * @param size the page size.
     * @return the new size option.
     */
    static ImmutableSizeOption of(final int size) {
        return new ImmutableSizeOption(size);
    }

    @Override
    public void accept(final OptionVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public boolean equals(final Object o) {
        if (o instanceof ImmutableSizeOption) {
            final ImmutableSizeOption that = (ImmutableSizeOption) o;
            return size == that.size;
        } else {
            return false;
        }
    }

    @Override
    public int getSize() {
        return size;
    }

    @Override
    public int hashCode() {
        return size;
    }

    @Override
    public String toString() {
        return "size(" + size + ")";
    }
}
