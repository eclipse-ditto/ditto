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
package org.eclipse.ditto.internal.utils.protocol;

import java.util.List;
import java.util.Map;

import org.eclipse.ditto.json.JsonPointer;

/**
 * Immutable model representing indexed partial access paths.
 * This format uses integer indices to reference subjects, reducing header size.
 *
 * <p>Format:</p>
 * <pre>{@code
 * {
 *   "subjects": ["subject1", "subject2"],
 *   "paths": {
 *     "/attributes/foo": [0],
 *     "/features/A/status": [1]
 *   }
 * }
 * }</pre>
 *
 * @param subjects the list of subject IDs (sorted alphabetically)
 * @param paths the map of JsonPointer paths to lists of subject indices
 * @since 3.9.0
 */
record IndexedPartialAccessPaths(
        List<String> subjects,
        Map<JsonPointer, List<Integer>> paths
) {}
