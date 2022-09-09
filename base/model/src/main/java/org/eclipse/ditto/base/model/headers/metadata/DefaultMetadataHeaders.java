/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.headers.metadata;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;

/**
 * Default implementation of {@link org.eclipse.ditto.base.model.headers.metadata.MetadataHeaders}.
 *
 * @since 1.2.0
 */
@NotThreadSafe
final class DefaultMetadataHeaders extends AbstractSet<MetadataHeader> implements MetadataHeaders {

    private final SortedSet<MetadataHeader> treeSet;

    private DefaultMetadataHeaders(final SortedSet<MetadataHeader> treeSet) {
        this.treeSet = treeSet;
    }

    /**
     * Creates a new instance of DefaultMetadataHeaders.
     *
     * @return the instance.
     */
    static DefaultMetadataHeaders newInstance() {
        return new DefaultMetadataHeaders(new TreeSet<>());
    }

    /**
     * Parses the CharSequence argument as an instance of DefaultMetadataHeaders.
     *
     * @param metadataHeadersCharSequence the CharSequence containing the DefaultMetadataHeaders JSON array
     * representation to be parsed. If the CharSequence is interpreted as an empty JSON array.
     * @return the instance.
     * @throws NullPointerException if {@code metadataHeadersCharSequence} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code metadataHeadersCharSequence} is not a JSON array or
     * if it contained an invalid JSON object representation of a MetadataHeader.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code metadataHeadersCharSequence} contained
     * a metadata JSON object with a missing field.
     * @see org.eclipse.ditto.base.model.headers.metadata.MetadataHeader#fromJson(JsonObject)
     */
    static DefaultMetadataHeaders parseMetadataHeaders(final CharSequence metadataHeadersCharSequence) {
        checkNotNull(metadataHeadersCharSequence, "metadataHeadersCharSequence");
        final DefaultMetadataHeaders result;
        if (0 < metadataHeadersCharSequence.length()) {
            result = parseJsonArray(JsonArray.of(metadataHeadersCharSequence.toString()));
        } else {
            result = newInstance();
        }
        return result;
    }

    private static DefaultMetadataHeaders parseJsonArray(final JsonArray jsonArray) {
        final TreeSet<MetadataHeader> delegationTarget = jsonArray.stream()
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .map(MetadataHeader::fromJson)
                .collect(Collectors.toCollection(TreeSet::new));

        return new DefaultMetadataHeaders(delegationTarget);
    }

    @Override
    public JsonArray toJson() {
        return treeSet.stream().map(MetadataHeader::toJson).collect(JsonCollectors.valuesToArray());
    }

    @Override
    public boolean add(final MetadataHeader metadataHeader) {
        return treeSet.add(metadataHeader);
    }

    @Override
    public boolean addAll(final Collection<? extends MetadataHeader> c) {
        return treeSet.addAll(c);
    }

    @Override
    public Iterator<MetadataHeader> iterator() {
        return treeSet.iterator();
    }

    @Override
    public boolean remove(final Object o) {
        return treeSet.remove(o);
    }

    @Override
    public boolean removeAll(final Collection<?> c) {
        return treeSet.removeAll(c);
    }

    @Override
    public boolean retainAll(final Collection<?> c) {
        return treeSet.retainAll(c);
    }

    @Override
    public void clear() {
        treeSet.clear();
    }

    @Override
    public int size() {
        return treeSet.size();
    }

    @Override
    public Comparator<? super MetadataHeader> comparator() {
        return treeSet.comparator();
    }

    @Override
    public SortedSet<MetadataHeader> subSet(final MetadataHeader metadataHeader, final MetadataHeader e1) {
        return treeSet.subSet(metadataHeader, e1);
    }

    @Override
    public SortedSet<MetadataHeader> headSet(final MetadataHeader metadataHeader) {
        return treeSet.headSet(metadataHeader);
    }

    @Override
    public SortedSet<MetadataHeader> tailSet(final MetadataHeader metadataHeader) {
        return treeSet.tailSet(metadataHeader);
    }

    @Override
    public MetadataHeader first() {
        return treeSet.first();
    }

    @Override
    public MetadataHeader last() {
        return treeSet.last();
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final DefaultMetadataHeaders that = (DefaultMetadataHeaders) o;
        return Objects.equals(treeSet, that.treeSet);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), treeSet);
    }

}
