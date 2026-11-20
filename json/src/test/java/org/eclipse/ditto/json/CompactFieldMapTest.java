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
package org.eclipse.ditto.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

/**
 * Unit test for {@link CompactFieldMap}.
 */
public final class CompactFieldMapTest {

    @Test
    public void smallMapBehavesLikeLinkedHashMap() {
        assertBehavesLikeLinkedHashMap(fields(5));
    }

    @Test
    public void indexedMapBehavesLikeLinkedHashMap() {
        assertBehavesLikeLinkedHashMap(fields(100));
    }

    @Test
    public void emptyMapBehavesLikeLinkedHashMap() {
        assertBehavesLikeLinkedHashMap(new ArrayList<>());
    }

    @Test
    public void duplicateKeysInSmallMapKeepFirstPositionAndLastField() {
        assertBehavesLikeLinkedHashMap(withDuplicates(fields(5)));
    }

    @Test
    public void duplicateKeysInIndexedMapKeepFirstPositionAndLastField() {
        assertBehavesLikeLinkedHashMap(withDuplicates(fields(40)));
    }

    @Test
    public void unknownKeysAreNotFound() {
        final CompactFieldMap small = CompactFieldMap.of(fields(3));
        final CompactFieldMap indexed = CompactFieldMap.of(fields(50));

        for (final CompactFieldMap map : new CompactFieldMap[]{small, indexed}) {
            assertThat(map.get("unknown")).isNull();
            assertThat(map.containsKey("unknown")).isFalse();
            assertThat(map.get(42)).isNull();
            assertThat(map.get(null)).isNull();
        }
    }

    @Test
    public void isImmutable() {
        final CompactFieldMap map = CompactFieldMap.of(fields(3));
        final Iterator<JsonField> iterator = map.values().iterator();
        iterator.next();

        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(iterator::remove);
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> map.put("k", JsonField.newInstance("k", JsonValue.of(1))));
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> map.remove("k0"));
    }

    @Test
    public void objectsWithCompactFieldsEqualObjectsBuiltFromLinkedHashMaps() {
        final List<JsonField> fields = fields(20);
        final Map<String, JsonField> linkedHashMap = toLinkedHashMap(fields);

        final JsonObject fromParsedFields = ImmutableJsonObject.ofParsedFields(fields, null);
        final JsonObject fromMap = ImmutableJsonObject.of(linkedHashMap);

        assertThat(fromParsedFields).isEqualTo(fromMap);
        assertThat(fromParsedFields.hashCode()).isEqualTo(fromMap.hashCode());
        assertThat(fromParsedFields.toString()).isEqualTo(fromMap.toString());
    }

    private static void assertBehavesLikeLinkedHashMap(final List<JsonField> fields) {
        final Map<String, JsonField> expected = toLinkedHashMap(fields);
        final CompactFieldMap actual = CompactFieldMap.of(fields);

        assertThat(actual).isEqualTo(expected);
        assertThat(expected).isEqualTo(actual);
        assertThat(actual.hashCode()).isEqualTo(expected.hashCode());
        assertThat(actual.size()).isEqualTo(expected.size());
        assertThat(actual.isEmpty()).isEqualTo(expected.isEmpty());
        assertThat(new ArrayList<>(actual.keySet())).containsExactlyElementsOf(expected.keySet());
        assertThat(new ArrayList<>(actual.values())).containsExactlyElementsOf(expected.values());
        assertThat(new ArrayList<>(actual.entrySet())).containsExactlyElementsOf(expected.entrySet());
        for (final String key : expected.keySet()) {
            assertThat(actual.get(key)).isSameAs(expected.get(key));
            assertThat(actual.containsKey(key)).isTrue();
            assertThat(actual.keySet().contains(key)).isTrue();
        }
    }

    private static Map<String, JsonField> toLinkedHashMap(final List<JsonField> fields) {
        final Map<String, JsonField> map = new LinkedHashMap<>();
        for (final JsonField field : fields) {
            map.put(field.getKeyName(), field);
        }
        return map;
    }

    private static List<JsonField> fields(final int count) {
        final List<JsonField> fields = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            fields.add(JsonField.newInstance("k" + i, JsonValue.of(i)));
        }
        return fields;
    }

    private static List<JsonField> withDuplicates(final List<JsonField> fields) {
        final List<JsonField> result = new ArrayList<>(fields);
        result.add(JsonField.newInstance("k1", JsonValue.of("replaced")));
        result.add(JsonField.newInstance("k0", JsonValue.of("replaced too")));
        result.add(JsonField.newInstance("k1", JsonValue.of("replaced again")));
        return result;
    }

}
