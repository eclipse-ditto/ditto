/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.headers;

import javax.annotation.Nullable;

/**
 * This interface represents the definition of a pre-defined Ditto header. The definition provides the header key as
 * well as the Java type of the header value.
 * Note: All header keys must be lower-case;
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
    @SuppressWarnings({"rawtypes", "java:S3740"})
    Class getJavaType();

    /**
     * Returns the type to which this header value should be serialized.
     *
     * @return the serialization type
     */
    @SuppressWarnings({"rawtypes", "java:S3740"})
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
     * @throws org.eclipse.ditto.base.model.exceptions.DittoHeaderInvalidException if {@code value} is not a valid
     * representation of this definition's Java type.
     * @see #getJavaType()
     */
    void validateValue(@Nullable CharSequence value);

    /**
     * Same as {@link #getKey()}.
     *
     * @return the key of this enum constant.
     */
    @Override
    String toString();

}
