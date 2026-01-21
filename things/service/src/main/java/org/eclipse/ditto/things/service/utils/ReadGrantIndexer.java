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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.ditto.json.JsonPointer;

/**
 * Utility class for converting ReadGrant to IndexedReadGrant format.
 * Uses integer indices to reference subjects, significantly reducing header size.
 */
public final class ReadGrantIndexer {

    private ReadGrantIndexer() {
        // No instantiation
    }

    /**
     * Converts a ReadGrant to IndexedReadGrant format.
     * Assigns integer indices to subjects and maps paths to arrays of indices.
     *
     * @param grant the ReadGrant to index
     * @return IndexedReadGrant with subject indices
     */
    public static IndexedReadGrant index(final ReadGrant grant) {
        if (grant.isEmpty()) {
            return IndexedReadGrant.empty();
        }

        final Map<String, Integer> subjectToIndex = new LinkedHashMap<>();
        final Map<Integer, String> indexToSubject = new LinkedHashMap<>();
        final Map<String, List<Integer>> pathToIndices = new LinkedHashMap<>();

        int idx = 0;

        for (final Map.Entry<JsonPointer, Set<String>> entry : grant.pointerToSubjects().entrySet()) {
            final String path = PointerUtils.toStringWithoutLeadingSlash(entry.getKey());
            final List<Integer> indices = new ArrayList<>();

            final List<String> sortedSubjects = entry.getValue().stream().sorted().toList();
            for (final String subjectId : sortedSubjects) {
                Integer index = subjectToIndex.get(subjectId);
                if (index == null) {
                    index = idx++;
                    subjectToIndex.put(subjectId, index);
                    indexToSubject.put(index, subjectId);
                }
                indices.add(index);
            }

            pathToIndices.put(path, indices);
        }

        return new IndexedReadGrant(pathToIndices, indexToSubject);
    }
}

