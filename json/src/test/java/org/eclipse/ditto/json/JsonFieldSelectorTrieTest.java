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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Test;

/**
 * Unit test for {@link JsonFieldSelectorTrie}.
 */
public final class JsonFieldSelectorTrieTest {

    @Test
    public void trieWithEmptyPathIsEmpty() {
        assertThat(JsonFieldSelectorTrie.of(Collections.singleton(JsonFactory.emptyPointer())).isEmpty()).isTrue();
    }

    @Test
    public void trieWithNonemptyPaths() {
        final JsonFieldSelectorTrie underTest =
                JsonFieldSelectorTrie.of(JsonFieldSelector.newInstance("a/b/c/d", "a/b/e", "b/b/c"));

        assertThat(getDescendantKeys(underTest)).isEqualTo(keySetOf("a", "b"));
        assertThat(getDescendantKeys(underTest, "a")).isEqualTo(keySetOf("b"));
        assertThat(getDescendantKeys(underTest, "a", "b")).isEqualTo(keySetOf("c", "e"));
        assertThat(getDescendantKeys(underTest, "a", "b", "c")).isEqualTo(keySetOf("d"));
        assertThat(getDescendantKeys(underTest, "a", "b", "c", "d")).isEmpty();
        assertThat(getDescendantKeys(underTest, "a", "b", "e")).isEmpty();
        assertThat(getDescendantKeys(underTest, "b")).isEqualTo(keySetOf("b"));
        assertThat(getDescendantKeys(underTest, "b", "b")).isEqualTo(keySetOf("c"));
        assertThat(getDescendantKeys(underTest, "b", "b", "c")).isEmpty();
        assertThat(getDescendantKeys(underTest, "c")).isEmpty();
    }

    @Test
    public void trieWithJsonObjectAndCertainFieldInSameJsonObject() {
        // given
        // a field selector containing both, a "field" inside an "object" and the "object" itself
        final JsonFieldSelector fieldSelector = JsonFieldSelector.newInstance("object/field", "object");

        // when
        final JsonFieldSelectorTrie underTest = JsonFieldSelectorTrie.of(fieldSelector);

        // then
        assertThat(getDescendantKeys(underTest)).isEqualTo(keySetOf("object"));
        // ensure that not only the "field" is contained, but the complete "object" instead:
        assertThat(getDescendantKeys(underTest, "object")).isEmpty();
    }

    @Test
    public void trieWithJsonObjectAndCertainFieldInSameJsonObjectOrderSwapped() {
        // given
        // a field selector containing both, a "field" inside an "object" and the "object" itself
        final JsonFieldSelector fieldSelectorReverse = JsonFieldSelector.newInstance("object", "object/field");

        // when
        final JsonFieldSelectorTrie underTestReverse = JsonFieldSelectorTrie.of(fieldSelectorReverse);

        // then
        assertThat(getDescendantKeys(underTestReverse)).isEqualTo(keySetOf("object"));
        // ensure that not only the "field" is contained, but the complete "object" instead:
        assertThat(getDescendantKeys(underTestReverse, "object")).isEmpty();
    }

    private static Set<JsonKey> keySetOf(final String... keyNames) {
        return Arrays.stream(keyNames).map(JsonKey::of).collect(Collectors.toSet());
    }

    private static Set<JsonKey> getDescendantKeys(final JsonFieldSelectorTrie trie, final String... keyNames) {
        if (keyNames.length > 0) {
            return getDescendantKeys(trie.descend(JsonKey.of(keyNames[0])),
                    Arrays.copyOfRange(keyNames, 1, keyNames.length));
        } else {
            return trie.getKeys();
        }
    }

}
