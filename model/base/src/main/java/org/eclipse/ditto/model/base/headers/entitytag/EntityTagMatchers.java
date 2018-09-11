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

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.Stream;

import javax.annotation.concurrent.Immutable;

/**
 * Java representation for a List of {@link EntityTagMatcher}.
 */
@Immutable
public final class EntityTagMatchers implements Iterable<EntityTagMatcher> {

    private final List<EntityTagMatcher> entityTagMatcherList;

    private EntityTagMatchers(final List<EntityTagMatcher> entityTagMatcherList) {
        checkNotNull(entityTagMatcherList, "entityTagMatcherList");
        this.entityTagMatcherList = Collections.unmodifiableList(new ArrayList<>(entityTagMatcherList));
    }

    /**
     * Builds {@link EntityTagMatchers} from a String that contains comma separated values of entity-tag-matchers.
     * Spaces before and after the comma will be removed.
     *
     * @param commaSeparatedEntityTagString The String that contains comma separated values of entity-tag-matchers.
     * @return {@link EntityTagMatchers} that contain all {@link EntityTagMatcher entity-tag-matchers} of the comma
     * separated String.
     * @throws org.eclipse.ditto.model.base.exceptions.DittoHeaderInvalidException if one of the values in the comma
     * separated String is neither an {@link EntityTagMatcher#ASTERISK} nor a valid {@link EntityTag} according to
     * {@link EntityTag#isValid(String)}.
     */
    public static EntityTagMatchers fromCommaSeparatedString(final String commaSeparatedEntityTagString) {
        return fromStrings(commaSeparatedEntityTagString.split("\\s*,\\s*"));
    }

    /**
     * Builds {@link EntityTagMatchers} from a Strings that contain values of entity-tag-matchers.
     *
     * @param entityTagMatcherStrings The Strings that contain values of entity-tag-matchers.
     * @return {@link EntityTagMatchers} that contain all {@link EntityTagMatcher entity-tag-matchers} of the Strings.
     * @throws org.eclipse.ditto.model.base.exceptions.DittoHeaderInvalidException if one of the Strings is not a valid
     * {@link EntityTag} according to {@link EntityTag#isValid(String)}.
     */
    public static EntityTagMatchers fromStrings(final String... entityTagMatcherStrings) {
        return fromList(Arrays.stream(entityTagMatcherStrings).map(EntityTagMatcher::fromString).collect(toList()));
    }

    /**
     * Builds {@link EntityTagMatchers} from a List of {@link EntityTagMatcher entity-tag-matchers}.
     *
     * @param entityTagMatchers The list of {@link EntityTagMatcher entity-tag-matchers} that should be contained in
     * the new instance of {@link EntityTagMatchers}.
     * @return An instance of {@link EntityTagMatchers} containing the given list of
     * {@link EntityTagMatcher entity-tag-matchers}.
     */
    public static EntityTagMatchers fromList(final List<EntityTagMatcher> entityTagMatchers) {
        return new EntityTagMatchers(entityTagMatchers);
    }

    @Override
    public String toString() {
        return stream().map(EntityTagMatcher::toString).collect(joining(","));
    }

    public boolean isEmpty() {
        return entityTagMatcherList.isEmpty();
    }

    public Stream<EntityTagMatcher> stream() {
        return entityTagMatcherList.stream();
    }

    @Override
    public Iterator<EntityTagMatcher> iterator() {
        return entityTagMatcherList.iterator();
    }

    @Override
    public void forEach(final Consumer<? super EntityTagMatcher> action) {
        this.entityTagMatcherList.forEach(action);
    }

    @Override
    public Spliterator<EntityTagMatcher> spliterator() {
        return this.entityTagMatcherList.spliterator();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final EntityTagMatchers that = (EntityTagMatchers) o;
        return Objects.equals(entityTagMatcherList, that.entityTagMatcherList);
    }

    @Override
    public int hashCode() {

        return Objects.hash(entityTagMatcherList);
    }
}
