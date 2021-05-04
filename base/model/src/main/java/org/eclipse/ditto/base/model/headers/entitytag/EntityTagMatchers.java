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
package org.eclipse.ditto.base.model.headers.entitytag;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Java representation for a List of {@link org.eclipse.ditto.base.model.headers.entitytag.EntityTagMatcher}.
 */
@Immutable
public final class EntityTagMatchers implements Iterable<EntityTagMatcher> {

    /**
     * Regular expression pattern for splitting a comma separated CharSequence of {@link org.eclipse.ditto.base.model.headers.entitytag.EntityTagMatcher}s.
     */
    public static final Pattern ENTITY_TAG_MATCHERS_PATTERN = Pattern.compile("\\s*,\\s*");

    private final List<EntityTagMatcher> entityTagMatcherList;

    private EntityTagMatchers(final List<EntityTagMatcher> entityTagMatcherList) {
        checkNotNull(entityTagMatcherList, "entityTagMatcherList");
        this.entityTagMatcherList = Collections.unmodifiableList(new ArrayList<>(entityTagMatcherList));
    }

    /**
     * Builds {@code EntityTagMatchers} from a String that contains comma separated values of entity-tag-matchers.
     * Spaces before and after the comma will be removed.
     *
     * @param commaSeparatedEntityTagString the String that contains comma separated values of
     * {@link org.eclipse.ditto.base.model.headers.entitytag.EntityTagMatcher}s.
     * @return EntityTagMatchers that contain all EntityTagMatchers of the comma separated String.
     * @throws org.eclipse.ditto.base.model.exceptions.DittoHeaderInvalidException if one of the values in the comma
     * separated String is neither an {@value org.eclipse.ditto.base.model.headers.entitytag.EntityTagMatcher#ASTERISK} nor a valid {@link org.eclipse.ditto.base.model.headers.entitytag.EntityTag} according to
     * {@link org.eclipse.ditto.base.model.headers.entitytag.EntityTag#isValid(CharSequence)}.
     */
    public static EntityTagMatchers fromCommaSeparatedString(final String commaSeparatedEntityTagString) {
        return fromStrings(ENTITY_TAG_MATCHERS_PATTERN.split(commaSeparatedEntityTagString));
    }

    /**
     * Builds {@code EntityTagMatchers} from a Strings that contain values of entity-tag-matchers.
     *
     * @param entityTagMatcherStrings The Strings that contain values of entity-tag-matchers.
     * @return EntityTagMatchers that contain all {@link org.eclipse.ditto.base.model.headers.entitytag.EntityTagMatcher}s of the given Strings.
     * @throws org.eclipse.ditto.base.model.exceptions.DittoHeaderInvalidException if one of the Strings is not a valid
     * {@link org.eclipse.ditto.base.model.headers.entitytag.EntityTag} according to {@link org.eclipse.ditto.base.model.headers.entitytag.EntityTag#isValid(CharSequence)}.
     */
    public static EntityTagMatchers fromStrings(final String... entityTagMatcherStrings) {
        return fromList(Arrays.stream(entityTagMatcherStrings).map(EntityTagMatcher::fromString).collect(toList()));
    }

    /**
     * Builds {@code EntityTagMatchers} from a List of {@link org.eclipse.ditto.base.model.headers.entitytag.EntityTagMatcher}s.
     *
     * @param entityTagMatchers the list of EntityTagMatchers that should be contained in the returned instance.
     * @return an instance of EntityTagMatchers containing the given list of EntityTagMatchers.
     * @throws NullPointerException if {@code entityTagMatchers} is {@code null}.
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
        entityTagMatcherList.forEach(action);
    }

    @Override
    public Spliterator<EntityTagMatcher> spliterator() {
        return entityTagMatcherList.spliterator();
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final EntityTagMatchers that = (EntityTagMatchers) o;
        return Objects.equals(entityTagMatcherList, that.entityTagMatcherList);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityTagMatcherList);
    }

}
