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
package org.eclipse.ditto.model.base.headers.entitytag;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.concurrent.Immutable;

/**
 * Java representation for a List of {@link EntityTag}.
 */
@Immutable
public final class EntityTags implements Iterable<EntityTag> {

    private final List<EntityTag> entityTagList;

    private EntityTags(final List<EntityTag> entityTagList) {
        checkNotNull(entityTagList, "entityTagList");
        this.entityTagList = Collections.unmodifiableList(new ArrayList<>(entityTagList));
    }

    /**
     * Builds {@link EntityTags} from a String that contains comma separated values of entity-tags.
     * Spaces before and after the comma will be removed.
     *
     * @param commaSeparatedEntityTagString The String that contains comma separated values of entity-tags.
     * @return {@link EntityTags} that contain all {@link EntityTag entity-tags} of the comma separated String.
     * @throws IllegalArgumentException if one of the values in the comma separated String is not a valid
     * {@link EntityTag} according to {@link EntityTag#validate(String)}.
     */
    public static EntityTags fromCommaSeparatedString(final String commaSeparatedEntityTagString) {
        return fromStrings(commaSeparatedEntityTagString.split("\\s*,\\s*"));
    }

    /**
     * Builds {@link EntityTags} from a Strings that contain values of entity-tags.
     *
     * @param entityTagStrings The Strings that contain values of entity-tags.
     * @return {@link EntityTags} that contain all {@link EntityTag entity-tags} of the Strings.
     * @throws IllegalArgumentException if one of the Strings is not a valid {@link EntityTag} according to
     * {@link EntityTag#validate(String)}.
     */
    public static EntityTags fromStrings(final String... entityTagStrings) {
        return fromList(Arrays.stream(entityTagStrings).map(EntityTag::fromString).collect(Collectors.toList()));
    }

    /**
     * Builds {@link EntityTags} from a List of {@link EntityTag entity-tags}.
     *
     * @param entityTags The list of {@link EntityTag entity-tags} that should be contained in the new instance of
     * {@link EntityTags}.
     * @return An instance of {@link EntityTags} containing the given list of {@link EntityTag entity-tags}.
     */
    public static EntityTags fromList(final List<EntityTag> entityTags) {
        return new EntityTags(entityTags);
    }

    @Override
    public String toString() {
        return stream().map(EntityTag::toString).collect(Collectors.joining(","));
    }

    public boolean isEmpty() {
        return entityTagList.isEmpty();
    }

    public Stream<EntityTag> stream() {
        return entityTagList.stream();
    }

    @Override
    public Iterator<EntityTag> iterator() {
        return entityTagList.iterator();
    }

    @Override
    public void forEach(final Consumer<? super EntityTag> action) {
        this.entityTagList.forEach(action);
    }

    @Override
    public Spliterator<EntityTag> spliterator() {
        return this.entityTagList.spliterator();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final EntityTags that = (EntityTags) o;
        return Objects.equals(entityTagList, that.entityTagList);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityTagList);
    }

}
