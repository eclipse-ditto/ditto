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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonPointer;

/**
 * Trie data structure for efficient path prefix matching.
 * Optimized for checking if a JsonPointer has any accessible child paths.
 */
final class PathTrie {

    private final Set<JsonPointer> exactPaths;
    private final Map<JsonKey, PathTrie> children;
    private boolean hasDescendants;

    private PathTrie() {
        this.exactPaths = new HashSet<>();
        this.children = new HashMap<>();
        this.hasDescendants = false;
    }

    /**
     * Creates a PathTrie from a set of accessible paths.
     *
     * @param accessiblePaths the set of accessible JsonPointer paths
     * @return a PathTrie for efficient prefix matching
     */
    static PathTrie fromPaths(final Set<JsonPointer> accessiblePaths) {
        if (accessiblePaths.isEmpty()) {
            return new PathTrie();
        }

        final PathTrie trie = new PathTrie();
        for (final JsonPointer path : accessiblePaths) {
            trie.addPath(path);
        }
        return trie;
    }

    /**
     * Adds a path to the trie.
     */
    private void addPath(final JsonPointer path) {
        if (path.isEmpty()) {
            exactPaths.add(path);
            hasDescendants = true;
            return;
        }

        exactPaths.add(path);
        PathTrie current = this;
        final int levelCount = path.getLevelCount();
        
        if (levelCount > 0) {
            current.hasDescendants = true;
        }
        
        for (int i = 0; i < levelCount; i++) {
            final JsonKey key = path.get(i).orElseThrow();
            
            current = current.children.computeIfAbsent(key, k -> new PathTrie());
            
            current.hasDescendants = true;
        }
    }

    /**
     * Checks if the given path is exactly accessible.
     *
     * @param path the path to check
     * @return true if the path is exactly accessible
     */
    boolean isExactMatch(final JsonPointer path) {
        return exactPaths.contains(path);
    }

    /**
     * Checks if the given path has any accessible descendants.
     * This is optimized for checking if a parent path should be included
     * because it has accessible children.
     *
     * @param path the path to check
     * @return true if any descendant of the path is accessible
     */
    boolean hasAccessibleDescendant(final JsonPointer path) {
        if (path.isEmpty()) {
            return hasDescendants || !exactPaths.isEmpty();
        }

        PathTrie current = this;
        for (int i = 0; i < path.getLevelCount(); i++) {
            final JsonKey key = path.get(i).orElseThrow();
            current = current.children.get(key);
            if (current == null) {
                return false;
            }
        }
        
        return current.hasDescendants || !current.exactPaths.isEmpty();
    }

}

