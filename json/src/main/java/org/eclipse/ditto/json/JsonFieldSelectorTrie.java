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
package org.eclipse.ditto.json;

import static java.util.Objects.requireNonNull;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * Package-private trie representation of a {@code JsonFieldSelector}. The trie structure is easier to traverse
 * a {@code JsonObject} by, since it requires no merging of sub-objects.
 * <p>
 * A normal {@code JsonFieldSelector} is a list of {@code JsonPointer}s, for example:
 * <pre>
 * JsonPointer 1: a -> b -> c -> d
 * JsonPointer 2: a -> b -> e
 * JsonPointer 3: b -> b -> c
 * </pre>
 * {@code JsonFieldSelectorTrie} represents the list as trie by collecting lists with the same prefix:
 * <pre>{@code
 *
 *                                     +-------> c +-------> d
 *                                     |
 *                                     |
 *                                     |
 *                                     |
 *         +---------> a +---------> b +-------> e
 *         |
 *    +----+
 *    |root|
 *    +----+
 *         |
 *         +---------> b +---------> b +-------> c
 *
 * }</pre>
 * </p>
 */
@NotThreadSafe
final class JsonFieldSelectorTrie {

    /**
     * Children of the trie.
     */
    private final Map<JsonKey, JsonFieldSelectorTrie> children;

    private JsonFieldSelectorTrie() {
        children = new LinkedHashMap<>();
    }

    /**
     * Creates a trie from the specified JsonPointers.
     *
     * @param jsonPointers collection of JSON pointers which form the returned trie.
     * @return trie representation of the collection.
     * @throws NullPointerException if {@code jsonPointers} is {@code null}.
     */
    static JsonFieldSelectorTrie of(final Iterable<JsonPointer> jsonPointers) {
        requireNonNull(jsonPointers, "The JSON pointers must not be null!");

        final JsonFieldSelectorTrie trie = new JsonFieldSelectorTrie();
        jsonPointers.forEach(trie::add);
        return trie;
    }

    /**
     * Mutates the trie minimally so that the path of JsonKeys in the JsonPointer exists in the trie.
     *
     * @param jsonPointer the path to expand.
     * @return this trie after adding the path in JSON pointer.
     * @throws NullPointerException if {@code jsonPointer} is {@code null}.
     */
    JsonFieldSelectorTrie add(final JsonPointer jsonPointer) {
        requireNonNull(jsonPointer, "The JSON pointer to be added must not be null!");
        return addJsonKeyIterator(jsonPointer.iterator());
    }

    /**
     * Private: Add a path to this trie. The path is represented by an iterator of JsonKey for performance, so that
     * the time complexity of adding a path is linear in the length of the path and not quadratic.
     *
     * @param iterator iterator representation of a path.
     * @return this trie with the path added.
     */
    private JsonFieldSelectorTrie addJsonKeyIterator(final Iterator<JsonKey> iterator) {
        if (iterator.hasNext()) {
            final JsonKey key = iterator.next();
            children.compute(key, (theKey, theChild) -> {
                final JsonFieldSelectorTrie child = theChild != null ? theChild : new JsonFieldSelectorTrie();
                return child.addJsonKeyIterator(iterator);
            });
        }
        return this;
    }

    /**
     * Indicates whether this trie has any child.
     *
     * @return {@code true} if this trie has a child, {@code false} else.
     */
    boolean isEmpty() {
        return children.isEmpty();
    }

    /**
     * Returns the keys of the children.
     *
     * @return the keys.
     */
    Set<JsonKey> getKeys() {
        return children.keySet();
    }

    /**
     * Retrieves a child.
     *
     * @param key label of the child.
     * @return the child if it exists; an empty trie if it does not.
     */
    JsonFieldSelectorTrie descend(final JsonKey key) {
        final JsonFieldSelectorTrie child = children.get(key);
        return child != null ? child : new JsonFieldSelectorTrie();
    }

}
