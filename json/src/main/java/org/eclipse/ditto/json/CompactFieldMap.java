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

import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Immutable, insertion-ordered map of the fields of a JSON object, keyed by the fields' key names.
 * <p>
 * Holds the fields in a single exactly sized array instead of a {@code LinkedHashMap} with its table and one entry per
 * field. Small objects are searched linearly; objects with more than {@link #MAX_LINEAR_SIZE} fields get an
 * open-addressing index.
 * Like a {@code LinkedHashMap}, a key which occurs more than once keeps its first position and its last field.
 * </p>
 */
@Immutable
final class CompactFieldMap extends AbstractMap<String, JsonField> {

    static final CompactFieldMap EMPTY = new CompactFieldMap(new JsonField[0], null);

    private static final int MAX_LINEAR_SIZE = 8;

    private final JsonField[] fields;
    // slot -> field index + 1, 0 = free; null for small maps
    @Nullable private final int[] index;

    private CompactFieldMap(final JsonField[] fields, @Nullable final int[] index) {
        this.fields = fields;
        this.index = index;
    }

    /**
     * Takes over the passed array, which the caller must neither modify nor keep.
     */
    static CompactFieldMap ofOwnedArray(final JsonField[] fields) {
        if (0 == fields.length) {
            return EMPTY;
        }
        if (fields.length <= MAX_LINEAR_SIZE) {
            return new CompactFieldMap(withoutDuplicateKeys(fields), null);
        }
        final int[] index = buildIndex(fields);
        if (null != index) {
            return new CompactFieldMap(fields, index);
        }
        // duplicate keys (not produced by Ditto's own serialization): resolve like a LinkedHashMap would
        final Map<String, JsonField> map = new java.util.LinkedHashMap<>(fields.length * 2);
        for (final JsonField field : fields) {
            map.put(field.getKeyName(), field);
        }
        return ofOwnedArray(map.values().toArray(new JsonField[0]));
    }

    static CompactFieldMap of(final Collection<JsonField> fields) {
        return ofOwnedArray(fields.toArray(new JsonField[0]));
    }

    static CompactFieldMap of(final Map<String, JsonField> fieldMap) {
        if (fieldMap instanceof CompactFieldMap) {
            return (CompactFieldMap) fieldMap;
        }
        return ofOwnedArray(fieldMap.values().toArray(new JsonField[0]));
    }

    private static JsonField[] withoutDuplicateKeys(final JsonField[] fields) {
        int size = fields.length;
        for (int i = 1; i < size; i++) {
            final String key = fields[i].getKeyName();
            for (int j = 0; j < i; j++) {
                if (fields[j].getKeyName().equals(key)) {
                    fields[j] = fields[i];
                    System.arraycopy(fields, i + 1, fields, i, size - i - 1);
                    size--;
                    i--;
                    break;
                }
            }
        }
        return size == fields.length ? fields : Arrays.copyOf(fields, size);
    }

    /**
     * @return the index or {@code null} if a key occurs more than once.
     */
    @Nullable
    private static int[] buildIndex(final JsonField[] fields) {
        final int[] index = new int[Integer.highestOneBit(fields.length * 2 - 1) << 1];
        final int mask = index.length - 1;
        for (int i = 0; i < fields.length; i++) {
            final String keyName = fields[i].getKeyName();
            int slot = spread(keyName.hashCode()) & mask;
            int entry;
            while ((entry = index[slot]) != 0) {
                if (fields[entry - 1].getKeyName().equals(keyName)) {
                    return null;
                }
                slot = (slot + 1) & mask;
            }
            index[slot] = i + 1;
        }
        return index;
    }

    static boolean rangeEquals(final byte[] a, final int aOffset, final int aLength,
            final byte[] b, final int bOffset, final int bLength) {
        if (aLength != bLength) {
            return false;
        }
        for (int i = 0; i < aLength; i++) {
            if (a[aOffset + i] != b[bOffset + i]) {
                return false;
            }
        }
        return true;
    }

    private static int spread(final int hashCode) {
        return hashCode ^ (hashCode >>> 16);
    }

    private int indexOf(@Nullable final Object key) {
        if (!(key instanceof String)) {
            return -1;
        }
        final String keyName = (String) key;
        final int[] theIndex = index;
        if (null == theIndex) {
            for (int i = 0; i < fields.length; i++) {
                final String fieldKeyName = fields[i].getKeyName();
                if (fieldKeyName == keyName || fieldKeyName.equals(keyName)) {
                    return i;
                }
            }
            return -1;
        }
        final int mask = theIndex.length - 1;
        int slot = spread(keyName.hashCode()) & mask;
        int entry;
        while ((entry = theIndex[slot]) != 0) {
            final String fieldKeyName = fields[entry - 1].getKeyName();
            if (fieldKeyName == keyName || fieldKeyName.equals(keyName)) {
                return entry - 1;
            }
            slot = (slot + 1) & mask;
        }
        return -1;
    }

    List<JsonField> fieldList() {
        return Collections.unmodifiableList(Arrays.asList(fields));
    }

    @Override
    @Nullable
    public JsonField get(@Nullable final Object key) {
        final int i = indexOf(key);
        return i < 0 ? null : fields[i];
    }

    @Override
    public boolean containsKey(@Nullable final Object key) {
        return indexOf(key) >= 0;
    }

    @Override
    public int size() {
        return fields.length;
    }

    @Override
    public boolean isEmpty() {
        return 0 == fields.length;
    }

    @Override
    public Collection<JsonField> values() {
        return new AbstractCollection<JsonField>() {
            @Override
            public Iterator<JsonField> iterator() {
                return new ArrayIterator<>(fields, field -> field);
            }

            @Override
            public int size() {
                return fields.length;
            }
        };
    }

    @Override
    public Set<String> keySet() {
        return new AbstractSet<String>() {
            @Override
            public Iterator<String> iterator() {
                return new ArrayIterator<>(fields, JsonField::getKeyName);
            }

            @Override
            public boolean contains(final Object o) {
                return containsKey(o);
            }

            @Override
            public int size() {
                return fields.length;
            }
        };
    }

    @Override
    public Set<Entry<String, JsonField>> entrySet() {
        return new AbstractSet<Entry<String, JsonField>>() {
            @Override
            public Iterator<Entry<String, JsonField>> iterator() {
                return new ArrayIterator<>(fields, field -> new SimpleImmutableEntry<>(field.getKeyName(), field));
            }

            @Override
            public int size() {
                return fields.length;
            }
        };
    }

    @Override
    public int hashCode() {
        // same as AbstractMap: sum of the entries' hash codes, without creating the entries
        int result = 0;
        for (final JsonField field : fields) {
            result += field.getKeyName().hashCode() ^ field.hashCode();
        }
        return result;
    }

    private static final class ArrayIterator<T> implements Iterator<T> {

        private final JsonField[] fields;
        private final java.util.function.Function<JsonField, T> mapper;
        private int next;

        private ArrayIterator(final JsonField[] fields, final java.util.function.Function<JsonField, T> mapper) {
            this.fields = fields;
            this.mapper = mapper;
            next = 0;
        }

        @Override
        public boolean hasNext() {
            return next < fields.length;
        }

        @Override
        public T next() {
            if (next >= fields.length) {
                throw new NoSuchElementException();
            }
            return mapper.apply(fields[next++]);
        }
    }

}
