/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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

import java.util.Map;
import java.util.Set;

import org.eclipse.ditto.json.JsonPointer;

/**
 * Immutable model representing read grants: which subjects can read which paths.
 * Maps JsonPointer paths to sets of subject IDs.
 */
public record ReadGrant(Map<JsonPointer, Set<String>> pointerToSubjects) {

    /**
     * Creates an empty ReadGrant.
     *
     * @return empty ReadGrant
     */
    public static ReadGrant empty() {
        return new ReadGrant(Map.of());
    }

    /**
     * Checks if this grant is empty.
     *
     * @return true if no grants are present
     */
    public boolean isEmpty() {
        return pointerToSubjects.isEmpty();
    }
}

