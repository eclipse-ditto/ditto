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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * Package-private trie representation of a {@code JsonFieldSelector}. The trie structure is easier to traverse
 * a {@code JsonObject} by, since it requires no merging of sub-objects.
 * <p>
 * A normal {@code JsonFieldSelector} is a list of {@code JsonPointer}s, for example:
 * <pre>{@code
 * JsonPointer_1: a -> b -> c -> d
 * JsonPoniter_2: a -> b -> e
 * JsonPointer_3: b -> b -> c
 * }</pre>
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
    private final Map<JsonKey, JsonFieldSelectorTrie> children = new HashMap<>();

    /**
     * @return whether this trie has any child.
     */
    boolean isEmpty() {
        return children.isEmpty();
    }

    /**
     * @return set of labels of children.
     */
    Set<JsonKey> getKeys() {
        return children.keySet();
    }

    /**
     * retrieve a child.
     *
     * @param key label of the child.
     * @return the child if it exists; an empty trie if it does not.
     */
    JsonFieldSelectorTrie descend(final JsonKey key) {
        final JsonFieldSelectorTrie child = children.get(key);
        return child != null ? child : new JsonFieldSelectorTrie();
    }

    /**
     * Mutate the trie minimally so that the path of JsonKeys in the JsonPointer exists in the trie.
     *
     * @param jsonPointer the path to expand.
     * @return this trie after adding the path in json pointer.
     */
    JsonFieldSelectorTrie add(final JsonPointer jsonPointer) {
        final Iterator<JsonKey> iterator = jsonPointer.iterator();
        return addJsonKeyIterator(iterator);
    }

    /**
     * Create a trie from a collection of JsonPointers.
     *
     * @param jsonPointers collection of json pointers.
     * @return trie representation of the collection.
     */
    static JsonFieldSelectorTrie of(final Iterable<JsonPointer> jsonPointers) {
        final JsonFieldSelectorTrie trie = new JsonFieldSelectorTrie();
        jsonPointers.forEach(trie::add);
        return trie;
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
}
