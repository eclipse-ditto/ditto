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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Compares two {@link org.eclipse.ditto.base.model.headers.entitytag.EntityTag entity-tags} if not an {@link #ASTERISK_INSTANCE}.
 */
@Immutable
public final class EntityTagMatcher {

    /**
     * String constant that represents an asterisk.
     */
    static final String ASTERISK = "*";

    private static final EntityTagMatcher ASTERISK_INSTANCE = new EntityTagMatcher(null, true);

    @Nullable private final EntityTag entityTag;
    private final boolean isAsterisk;

    private EntityTagMatcher(@Nullable final EntityTag entityTag, final boolean isAsterisk) {
        this.entityTag = entityTag;
        this.isAsterisk = isAsterisk;
    }

    /**
     * Builds an {@code EntityTagMatcher} from a String value.
     *
     * @param entityTagMatcher the string representation of the entity-tag-matcher.
     * @return the EntityTagMatcher built from the given string value.
     * @throws NullPointerException if {@code EntityTagMatcher} is {@code null}.
     * @throws org.eclipse.ditto.base.model.exceptions.DittoHeaderInvalidException if the given {@code entityTagMatcher}
     * is not valid according to {@link #isValid(CharSequence)}.
     */
    public static EntityTagMatcher fromString(final String entityTagMatcher) {

        // use only one instance of asterisk in the JVM
        if (isAsterisk(entityTagMatcher)) {
            return ASTERISK_INSTANCE;
        }

        final EntityTag parsedEntityTag = EntityTag.fromString(entityTagMatcher);
        return new EntityTagMatcher(checkNotNull(parsedEntityTag, "entityTag"), false);
    }

    private static boolean isAsterisk(@Nullable final CharSequence entityTagMatcher) {
        boolean result = false;
        if (null != entityTagMatcher) {
            result = ASTERISK.equals(entityTagMatcher.toString());
        }
        return result;
    }

    /**
     * @return the {@code *} entity-tag value.
     */
    public static EntityTagMatcher asterisk() {
        return ASTERISK_INSTANCE;
    }

    /**
     * Checks if the given char sequence in terms of being a valid {@code EntityTagMatcher}
     *
     * @param entityTagMatcher the char sequence to validate.
     * @return {@code true} if the given entity tag matcher is valid, {@code false} else.
     */
    public static boolean isValid(@Nullable final CharSequence entityTagMatcher) {
        boolean result = false;
        if (null != entityTagMatcher) {
            result = isAsterisk(entityTagMatcher) || EntityTag.isValid(entityTagMatcher);
        }
        return result;
    }

    /**
     * Matches this {@code EntityTagMatcher} to the given {@code entityTagToMatch}.
     *
     * @param entityTagToMatch The {@link org.eclipse.ditto.base.model.headers.entitytag.EntityTag} to match against.
     * @return always {@code true} if this instance is {@link #isAsterisk}. Else returns result of
     * {@link org.eclipse.ditto.base.model.headers.entitytag.EntityTag#strongCompareTo(org.eclipse.ditto.base.model.headers.entitytag.EntityTag) strong comparison between two entity-tags}.
     */
    @SuppressWarnings("ConstantConditions")
    public boolean strongMatch(final EntityTag entityTagToMatch) {
        if (isAsterisk) {
            return true;
        }

        return entityTag.strongCompareTo(entityTagToMatch);
    }

    /**
     * Matches this {@code EntityTagMatcher} to the given {@code entityTagToMatch}.
     *
     * @param entityTagToMatch The {@link org.eclipse.ditto.base.model.headers.entitytag.EntityTag} to match against.
     * @return Always true if this instance is {@link #isAsterisk}. Else returns result of
     * {@link org.eclipse.ditto.base.model.headers.entitytag.EntityTag#weakCompareTo(org.eclipse.ditto.base.model.headers.entitytag.EntityTag)} weak comparison between two entity-tags}.
     */
    @SuppressWarnings("ConstantConditions")
    public boolean weakMatch(final EntityTag entityTagToMatch) {
        if (isAsterisk) {
            return true;
        }

        return entityTag.weakCompareTo(entityTagToMatch);
    }

    /**
     * Indicates whether this entity-tag equals {@value #ASTERISK}.
     *
     * @return {@code true} if entity-tag is an asterisk, {@code false} else.
     */
    public boolean isAsterisk() {
        return isAsterisk;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final EntityTagMatcher that = (EntityTagMatcher) o;
        return isAsterisk == that.isAsterisk && Objects.equals(entityTag, that.entityTag);
    }

    @Override
    public int hashCode() {
        return Objects.hash(isAsterisk, entityTag);
    }

    @Override
    @SuppressWarnings("ConstantConditions")
    public String toString() {
        return isAsterisk ? ASTERISK : entityTag.toString();
    }

}
