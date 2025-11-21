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
package org.eclipse.ditto.internal.utils.protocol;

import java.util.List;
import java.util.Map;

import org.eclipse.ditto.json.JsonPointer;

/**
 * Immutable model representing indexed partial access paths.
 * This format uses integer indices to reference subjects, reducing header size.
 *
 * <p>
 * Format:
 * <pre>{@code
 * {
 *   "subjects": ["subject1", "subject2"],
 *   "paths": {
 *     "/attributes/foo": [0],
 *     "/features/A/status": [1]
 *   }
 * }
 * }</pre>
 * </p>
 *
 * @since 3.9.0
 */
public record IndexedPartialAccessPaths(
        List<String> subjects,
        Map<JsonPointer, List<Integer>> paths
) {

    /**
     * Empty instance representing no partial access paths.
     */
    public static final IndexedPartialAccessPaths EMPTY =
            new IndexedPartialAccessPaths(List.of(), Map.of());

    /**
     * Checks if this instance is empty (no subjects or paths).
     *
     * @return true if empty, false otherwise
     */
    public boolean isEmpty() {
        return subjects.isEmpty() || paths.isEmpty();
    }
}

