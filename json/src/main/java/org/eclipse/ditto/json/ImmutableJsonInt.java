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

import java.util.Objects;

import javax.annotation.Nullable;
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
    public boolean isLong() {
        return true;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof AbstractJsonNumber) {
            final AbstractJsonNumber that = (AbstractJsonNumber) o;
            if (that.isInt()) {
                return Objects.equals(getValue(), that.asInt());
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        return getValue();
    }

}
