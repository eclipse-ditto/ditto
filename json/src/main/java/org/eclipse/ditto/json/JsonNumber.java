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

/**
 * This interface represents a JSON number.
 */
public interface JsonNumber extends JsonValue {

    /**
     * Indicates whether this number is an integer.
     *
     * @return {@code true} if an only if this number is an integer.
     */
    boolean isInt();

    /**
     * Indicates whether this number is of type long.
     *
     * @return {@code true} if an only if this number is of type long.
     */
    boolean isLong();

    /**
     * Indicates whether this number is of type double.
     *
     * @return {@code true} if an only if this number is of type double.
     */
    boolean isDouble();

}
