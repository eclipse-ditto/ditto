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
package org.eclipse.ditto.model.base.headers;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * This interface represents the definition of a pre-defined Ditto header. The definition provides the header key as
 * well as the Java type of the header value.
 */
public interface HeaderDefinition {

    /**
     * Returns the key used as key for header map.
     *
     * @return the key.
     */
    String getKey();

    /**
     * Returns the Java type of the header value which is associated with this definition's key.
     *
     * @return the Java type.
     * @see #getKey()
     */
    Class getJavaType();

    /**
     * Returns the type to which this header value should be serialized.
     *
     * @return the serialization type
     */
    Class getSerializationType();

    /**
     * Returns whether Ditto reads this header from headers sent by externals.
     *
     * @return whether externals are allowed to set this header.
     */
    boolean shouldReadFromExternalHeaders();

    /**
     * Returns whether Ditto publishes this header to externals.
     *
     * @return whether externals are allowed to see this header.
     */
    boolean shouldWriteToExternalHeaders();

    /**
     * Checks if the specified CharSequence is a valid representation of the Java type of this definition.
     * <p>
     * For example, if the Java type of this Definition was {@code int.class} then the value {@code "foo"} would be
     * invalid whereas the value {@code 42} would be recognised to be valid.
     * </p>
     *
     * @param value the value to be validated.
     * @throws IllegalArgumentException if {@code value} is not a valid representation of this definition's Java type.
     * @see #getJavaType()
     */
    default void validateValue(@Nullable final CharSequence value) {
        final HeaderValueValidator validator = HeaderValueValidator.getInstance();
        validator.accept(this, value);
    }

    /**
     * Same as {@link #getKey()}.
     *
     * @return the key of this enum constant.
     */
    @Nonnull
    @Override
    String toString();

}
