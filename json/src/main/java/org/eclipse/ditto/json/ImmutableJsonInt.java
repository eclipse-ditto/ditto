/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.json;

import javax.annotation.concurrent.Immutable;

/**
 * An immutable representation of a JSON number, which is known to fit in Java {@code int}.
 */
@Immutable
final class ImmutableJsonInt extends AbstractJsonNumber<Integer> {

    private ImmutableJsonInt(final int value) {
        super(value);
    }

    /**
     * Returns an instance of {@code ImmutableJsonInt}.
     *
     * @return the instance.
     */
    public static ImmutableJsonInt of(final int value) {
        return new ImmutableJsonInt(value);
    }

    @Override
    public boolean isInt() {
        return true;
    }

    @Override
    public int asInt() {
        return getValue();
    }

    @Override
    public long asLong() {
        return getValue();
    }

    @Override
    public boolean equals(final Object o) {
        if (super.equals(o)) {
            return true;
        }
        if (o instanceof AbstractJsonNumber) {
            final AbstractJsonNumber that = (AbstractJsonNumber) o;
            if (that.isLong()) {
                return getValue() == that.asLong();
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

}
