/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.model.thingsearch;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

/**
 * An immutable implementation of {@link LimitOption}.
 */
@Immutable
final class ImmutableLimitOption implements LimitOption {

    /**
     * The maximum allow value for count.
     */
    static final int MAX_COUNT = 200;

    private final int offset;
    private final int count;

    private ImmutableLimitOption(final int offset, final int count) {
        this.offset = offset;
        this.count = count;
    }

    /**
     * Returns a new instance of {@code ImmutableLimitOption} with the given offset and count.
     *
     * @param offset the offset to be set.
     * @param count the count to be set.
     * @return the new limit option.
     */
    public static ImmutableLimitOption of(final int offset, final int count) {
        return new ImmutableLimitOption(offset, count);
    }

    @Override
    public void accept(final OptionVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableLimitOption that = (ImmutableLimitOption) o;
        return offset == that.offset && count == that.count;
    }

    @Override
    public int getOffset() {
        return offset;
    }

    @Override
    public int getCount() {
        return count;
    }

    @Override
    public int hashCode() {
        return Objects.hash(offset, count);
    }

    @Override
    public String toString() {
        return "limit(" + offset + "," + count + ")";
    }

}
