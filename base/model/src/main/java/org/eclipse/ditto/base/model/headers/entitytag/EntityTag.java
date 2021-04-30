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
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.exceptions.DittoHeaderInvalidException;

/**
 * Implements an entity-tag according to
 * <a href="https://tools.ietf.org/html/rfc7232#section-2.3">rfc7232 - Section 2.3</a>
 */
@Immutable
public final class EntityTag {

    private static final String VALIDATION_ERROR_MESSAGE_TEMPLATE = "The opaque tag <%s> is not a valid entity-tag.";
    private static final Pattern ENTITY_TAG_REGEX_PATTERN = Pattern.compile(Regex.ENTITY_TAG);

    private static final String WEAK_PREFIX = "W/";

    private final boolean weak;

    private final String opaqueTag;

    private EntityTag(final boolean weak, final String opaqueTag) {
        if (!isValid(opaqueTag)) {
            throw DittoHeaderInvalidException.newBuilder()
                    .message(String.format(VALIDATION_ERROR_MESSAGE_TEMPLATE, opaqueTag))
                    .build();
        }
        this.weak = weak;
        this.opaqueTag = opaqueTag;
    }

    /**
     * Validates that the given char sequence is a quoted with an optional weak prefix.
     *
     * @param entityTag the char sequence that should get validated.
     * @return {@code true} if the given char sequence is valid, {@code false} if not.
     */
    public static boolean isValid(@Nullable final CharSequence entityTag) {
        boolean result = false;
        if (null != entityTag) {
            final Matcher matcher = ENTITY_TAG_REGEX_PATTERN.matcher(entityTag);
            result = matcher.matches();
        }
        return result;
    }

    /**
     * Gets the opaque-tag part of this entity-tag
     *
     * @return the opaque-tag part of this entity-tag
     */
    public String getOpaqueTag() {
        return opaqueTag;
    }

    /**
     * Indicates whether this entity-tag is a weak entity-tag
     *
     * @return True if entity-tag is weak. False if not.
     */
    public boolean isWeak() {
        return weak;
    }

    /**
     * Implements strong comparison based on <a href="https://tools.ietf.org/html/rfc7232#section-2.3.2">RFC 7232</a>
     *
     * @param otherEntityTag the EntityTag to compare to.
     * @return {@code true} if this EntityTag matches the given other entity tag based on strong comparison.
     */
    public boolean strongCompareTo(final EntityTag otherEntityTag) {
        if (isWeak()) {
            return false;
        }

        if (otherEntityTag.isWeak()) {
            return false;
        }

        return equals(otherEntityTag);
    }

    /**
     * Implements weak comparison based on <a href="https://tools.ietf.org/html/rfc7232#section-2.3.2">RFC 7232</a>
     *
     * @param otherEntityTag the EntityTag to compare to.
     * @return {@code true} if this EntityTag matches the given other entity tag based on weak comparison.
     */
    public boolean weakCompareTo(final EntityTag otherEntityTag) {
        return Objects.equals(getOpaqueTag(), otherEntityTag.getOpaqueTag());
    }

    /**
     * Builds an {@code EntityTag} from a String value.
     *
     * @param entityTag the string representation of the entity-tag.
     * @return the EntityTag built from the given string value.
     * @throws DittoHeaderInvalidException if the given {@code entityTag} is not valid according to
     * {@link #isValid(CharSequence)}.
     */
    public static EntityTag fromString(final String entityTag) {
        checkNotNull(entityTag);
        final boolean weak = entityTag.startsWith(WEAK_PREFIX);
        if (weak) {
            return weak(entityTag.substring(2));
        } else {
            return strong(entityTag);
        }
    }

    /**
     * Builds an {@code EntityTag} for the given {@code entity}.
     *
     * @param entity the entity for which an EntityTag should be built.
     * @return an optional for the EntityTag built from the given entity. Empty if no EntityTag could be built for the
     * given entity
     */
    public static Optional<EntityTag> fromEntity(@Nullable final Object entity) {
        return EntityTagBuilder.buildFromEntity(entity);
    }

    /**
     * Creates a strong entity-tag with the given {@code opaqueTag}.
     *
     * @param opaqueTag the opaque-tag of the entity-tag.
     * @return a strong entity-tag with the given {@code opaqueTag}.
     */
    public static EntityTag strong(final String opaqueTag) {
        return new EntityTag(false, opaqueTag);
    }

    /**
     * Creates a weak entity-tag with the given {@code opaqueTag}.
     *
     * @param opaqueTag the opaque-tag of the entity-tag.
     * @return a weak entity-tag with the given {@code opaqueTag}.
     */
    public static EntityTag weak(final String opaqueTag) {
        return new EntityTag(true, opaqueTag);
    }

    @Override
    public String toString() {
        final StringBuilder stringBuilder = new StringBuilder();
        if (isWeak()) {
            stringBuilder.append(WEAK_PREFIX);
        }
        stringBuilder.append(getOpaqueTag());

        return stringBuilder.toString();
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final EntityTag entityTag = (EntityTag) o;
        return weak == entityTag.weak && Objects.equals(opaqueTag, entityTag.opaqueTag);
    }

    @Override
    public int hashCode() {
        return Objects.hash(weak, opaqueTag);
    }

    private static final class Regex {

        private static final String WEAK_PREFIX = "(W/)";
        private static final String OPAQUE_TAG = "(\"[^\"*]*\")";
        private static final String ENTITY_TAG = WEAK_PREFIX + "?" + OPAQUE_TAG;

    }

}
