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
package org.eclipse.ditto.json;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

/**
 * Immutable implementation of {@link JsonParseOptions}.
 */
@Immutable
final class ImmutableJsonParseOptions implements JsonParseOptions {

    private final boolean applyUrlDecoding;

    private ImmutableJsonParseOptions(final boolean applyUrlDecoding) {
        this.applyUrlDecoding = applyUrlDecoding;
    }

    /**
     * Returns a new instance of {@code JsonParseOptions} with the specified options.
     *
     * @param applyUrlDecoding whether or not url decoding should be applied.
     * @return the new JsonParseOptions instance.
     */
    public static JsonParseOptions of(final boolean applyUrlDecoding) {
        return new ImmutableJsonParseOptions(applyUrlDecoding);
    }

    @Override
    public boolean isApplyUrlDecoding() {
        return applyUrlDecoding;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableJsonParseOptions that = (ImmutableJsonParseOptions) o;
        return applyUrlDecoding == that.applyUrlDecoding;
    }

    @Override
    public int hashCode() {
        return Objects.hash(applyUrlDecoding);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + "applyUrlDecoding=" + applyUrlDecoding + "]";
    }

}
