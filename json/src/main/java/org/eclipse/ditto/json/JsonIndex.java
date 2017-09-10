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

import javax.annotation.concurrent.Immutable;

/**
 * This interface represents a means for parsing a CharSequence to either a {@link JsonKey} or {@link JsonPointer}.
 * JsonIndex is a CharSequence, too, and delegates all of CharSequence's methods to the wrapped key or pointer; thus if
 * one is only interested in the string representation of JsonKey or JsonPointer, JsonIndex could be used as a common
 * abstraction instead.
 */
@Immutable
interface JsonIndex extends CharSequence {

    /**
     * Indicates whether this index represents a {@link JsonPointer}.
     *
     * @return {@code true} if this index provides a pointer, {@code false} else.
     * @see #asPointer()
     */
    boolean isPointer();

    /**
     * Indicates whether this index represents a {@link JsonKey}.
     *
     * @return {@code true} if this index provides a key, {@code false} else.
     * @see #asKey()
     */
    boolean isKey();

    /**
     * Returns this index as {@link JsonPointer}.
     *
     * @return this index as pointer.
     * @throws IllegalStateException if this index is not a pointer.
     * @see #isPointer()
     */
    JsonPointer asPointer();

    /**
     * Returns this index as {@link JsonKey}.
     *
     * @return this index as key.
     * @throws IllegalStateException if this index is not a key.
     * @see #isKey()
     */
    JsonKey asKey();

    /**
     * Returns the string representation of this index which is the representation of either a JsonKey or a JsonPointer.
     *
     * @return the string representation.
     */
    @Override
    String toString();

}
