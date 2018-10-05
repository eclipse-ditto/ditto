/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.model.base.headers.entitytag;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Compares two {@link EntityTag entity-tags} if not an {@link #ASTERISK_INSTANCE}.
 */
@Immutable
public final class EntityTagMatcher {

    private static final String ASTERISK = "*";
    private static final EntityTagMatcher ASTERISK_INSTANCE = new EntityTagMatcher();
    private final boolean isAsterisk;

    @Nullable
    private final EntityTag entityTag;

    private EntityTagMatcher() {
        this(null, true);
    }

    private EntityTagMatcher(final EntityTag entityTag) {
        this(entityTag, false);
        checkNotNull(entityTag, "entityTag");
    }

    private EntityTagMatcher(@Nullable final EntityTag entityTag, final boolean isAsterisk) {
        this.entityTag = entityTag;
        this.isAsterisk = isAsterisk;
    }

    /**
     * Builds an {@link EntityTagMatcher} from a String value.
     *
     * @param entityTagMatcher the string representation of the entity-tag-matcher.
     * @return The {@link EntityTagMatcher} built from the given string value.
     * @throws org.eclipse.ditto.model.base.exceptions.DittoHeaderInvalidException if the given {@code entityTagMatcher}
     * is not valid according to {@link #isValid(String)}.
     */
    public static EntityTagMatcher fromString(final String entityTagMatcher) {
        // use only one instance of asterisk in the JVM
        if (ASTERISK.equals(entityTagMatcher)) {
            return ASTERISK_INSTANCE;
        }

        return new EntityTagMatcher(EntityTag.fromString(entityTagMatcher));
    }

    /**
     * @return the {@code *} entity-tag value.
     */
    public static EntityTagMatcher asterisk() {
        return ASTERISK_INSTANCE;
    }


    /**
     * Matches this {@link EntityTagMatcher} to the given {@code entityTagToMatch}.
     *
     * @param entityTagToMatch The {@link EntityTag} to match against.
     * @return Always true if this instance is {@link #isAsterisk}. Else returns result of
     * {@link EntityTag#strongCompareTo(EntityTag) strong comparison between two entity-tags}.
     */
    public boolean strongMatch(final EntityTag entityTagToMatch) {
        if (isAsterisk) {
            return true;
        }

        return checkNotNull(entityTag).strongCompareTo(entityTagToMatch);
    }

    /**
     * Matches this {@link EntityTagMatcher} to the given {@code entityTagToMatch}.
     *
     * @param entityTagToMatch The {@link EntityTag} to match against.
     * @return Always true if this instance is {@link #isAsterisk}. Else returns result of
     * {@link EntityTag#weakCompareTo(EntityTag)} weak comparison between two entity-tags}.
     */
    public boolean weakMatch(final EntityTag entityTagToMatch) {
        if (isAsterisk) {
            return true;
        }

        return checkNotNull(entityTag).weakCompareTo(entityTagToMatch);
    }

    /**
     * Validates the given String in terms of being a valid {@link EntityTagMatcher}
     *
     * @param entityTagMatcher The String to validate
     * @return True if valid. False if not.
     */
    public static boolean isValid(String entityTagMatcher) {
        return ASTERISK.equals(entityTagMatcher) || EntityTag.isValid(entityTagMatcher);
    }


    /**
     * Indicates whether this entity-tag equals {@link #ASTERISK}.
     *
     * @return True if entity-tag equal {@link #ASTERISK}. False if not.
     */
    public boolean isAsterisk() {
        return this.isAsterisk;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final EntityTagMatcher that = (EntityTagMatcher) o;
        return isAsterisk == that.isAsterisk &&
                Objects.equals(entityTag, that.entityTag);
    }

    @Override
    public int hashCode() {

        return Objects.hash(isAsterisk, entityTag);
    }

    @Override
    public String toString() {
        return isAsterisk ? ASTERISK : checkNotNull(this.entityTag).toString();
    }
}
