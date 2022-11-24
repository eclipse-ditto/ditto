/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.metrics.instruments.tag;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * This class represents an unsorted and unmodifiable set of {@link Tag}s.
 */
@Immutable
public final class TagSet implements Iterable<Tag> {

    private static final TagSet EMPTY = new TagSet(Map.of());

    private final Map<String, Tag> tagMap;

    private TagSet(final Map<String, Tag> tagMap) {
        this.tagMap = Map.copyOf(tagMap);
    }

    /**
     * Returns an empty instance of {@code TagSet}.
     *
     * @return the empty instance.
     */
    public static TagSet empty() {
        return EMPTY;
    }

    /**
     * Returns a new instance of {@code TagSet} which contains only the
     * specified {@code Tag} argument.
     *
     * @param tag the tag which should be contained in the returned instance.
     * @return the new instance.
     * @throws NullPointerException if {@code tag} is {@code null}.
     */
    public static TagSet ofTag(final Tag tag) {
        checkNotNull(tag, "tag");
        return new TagSet(Map.of(tag.getKey(), tag));
    }

    /**
     * Returns a new instance of {@code TagSet} which contains the tags of the
     * specified {@code Collection} argument.
     *
     * @param tagCollection the Collection of which the tags should be
     * contained in the returned instance.
     * @return the new instance.
     * @throws NullPointerException if {@code tagCollection} is {@code null}.
     */
    public static TagSet ofTagCollection(final Collection<Tag> tagCollection) {
        checkNotNull(tagCollection, "tagCollection");
        return new TagSet(
                tagCollection.stream()
                        .collect(Collectors.toUnmodifiableMap(
                                Tag::getKey,
                                Function.identity(),
                                (oldValue, newValue) -> newValue)
                        )
        );
    }

    /**
     * Puts the specified {@code Tag} argument to a copy of this TagSet.
     * If this TagSet contained a Tag with the same key, then the entry in the
     * returned TagSet gets replaced by the specified Tag.
     *
     * @param tag the tag to be put.
     * @return a new instance of {@code TagSet} which contains {@code tag}.
     * @throws NullPointerException if {@code tag} is {@code null}.
     */
    public TagSet putTag(final Tag tag) {
        checkNotNull(tag, "tag");
        final var copyOfTagMap = getCopyOfTagMap();
        copyOfTagMap.put(tag.getKey(), tag);
        return new TagSet(copyOfTagMap);
    }

    private Map<String, Tag> getCopyOfTagMap() {
        return new HashMap<>(tagMap);
    }

    /**
     * Puts all tags of the specified {@code TagSet} argument to a copy of this
     * TagSet.
     * If this TagSet contained tags with the same key as ones that are
     * contained in the specified TagSet, then the entries in the returned
     * TagSet get replaced by the ones in the specified Tag.
     *
     * @param tagSet provides the tags to be put. If empty, this instance is
     * returned.
     * @return a new instance of {@code TagSet} which contains {@code tag}.
     * @throws NullPointerException if {@code tag} is {@code null}.
     */
    public TagSet putAllTags(final TagSet tagSet) {
        checkNotNull(tagSet, "tagSet");
        final TagSet result;
        if (tagSet.tagMap.isEmpty()) {
            result = this;
        } else {
            final var copyOfTagMap = getCopyOfTagMap();
            copyOfTagMap.putAll(tagSet.tagMap);
            result = new TagSet(copyOfTagMap);
        }
        return result;
    }

    /**
     * Indicates whether this TagSet contains a tag for the specified tag key
     * argument.
     *
     * @param tagKey the key to look after.
     * @return {@code true} this TagSet contains a tag with {@code tagKey} as
     * its key, {@code false} else.
     * @throws NullPointerException if {@code tagKey} is {@code null}.
     */
    public boolean containsKey(final String tagKey) {
        return tagMap.containsKey(checkNotNull(tagKey, "tagKey"));
    }

    /**
     * Returns the value of the tag which has the specified argument as key.
     *
     * @param tagKey the key to get the associated value for.
     * @return an Optional containing the associated value for {@code tagKey}
     * or an empty Option if {@code tagKey} is unknown.
     * @throws NullPointerException if {@code tagKey} is {@code null}.
     */
    public Optional<String> getTagValue(final String tagKey) {
        final Optional<String> result;
        @Nullable final var tag = tagMap.get(checkNotNull(tagKey, "tagKey"));
        if (null != tag) {
            result = Optional.of(tag.getValue());
        } else {
            result = Optional.empty();
        }
        return result;
    }

    @Override
    public Iterator<Tag> iterator() {
        return tagMap.values().iterator();
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final var that = (TagSet) o;
        return Objects.equals(tagMap, that.tagMap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tagMap);
    }

    /**
     * @return the plain string representation of this TagSets tags.
     */
    @Override
    public String toString() {
        return tagMap.values().toString();
    }

}
