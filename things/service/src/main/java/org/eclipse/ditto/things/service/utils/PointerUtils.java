/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.service.utils;

import org.eclipse.ditto.json.JsonPointer;

/**
 * Utility class for JsonPointer operations.
 * Provides consistent handling of pointer paths without raw string manipulation.
 */
public final class PointerUtils {

    private static final JsonPointer ROOT_POINTER = JsonPointer.of("/");

    private PointerUtils() {
        // No instantiation
    }

    /**
     * Checks if a pointer represents the root path.
     *
     * @param pointer the pointer to check
     * @return true if the pointer is root (empty or "/")
     */
    public static boolean isRoot(final JsonPointer pointer) {
        return pointer.isEmpty() || pointer.equals(ROOT_POINTER);
    }

    /**
     * Converts a pointer to a string representation without leading slash.
     * Useful for JSON keys to avoid nested structure.
     *
     * @param pointer the pointer to convert
     * @return string representation without leading slash
     */
    public static String toStringWithoutLeadingSlash(final JsonPointer pointer) {
        final String pointerString = pointer.toString();
        if (pointerString.startsWith("/")) {
            return pointerString.substring(1);
        }
        return pointerString;
    }
}

