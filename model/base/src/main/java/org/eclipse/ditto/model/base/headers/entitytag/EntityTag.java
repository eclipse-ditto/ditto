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
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.exceptions.DittoHeaderInvalidException;

/**
 * Implements an entity-tag according to
 * <a href="https://tools.ietf.org/html/rfc7232#section-2.3">rfc7232 - Section 2.3</a>
 */
@Immutable
public final class EntityTag {

    private static final String VALIDATION_ERROR_MESSAGE_TEMPLATE = "The opaque tag <%s> is not a valid entity-tag.";


    private static final String WEAK_PREFIX = "W/";

    private final boolean weak;

    private final String opaqueTag;

    private EntityTag(final boolean weak, final String opaqueTag) {
        if (!isValid(opaqueTag)) {
            final String errorMessage = String.format(VALIDATION_ERROR_MESSAGE_TEMPLATE, opaqueTag);
            throw DittoHeaderInvalidException.newCustomMessageBuilder(errorMessage).build();
        }
        this.weak = weak;
        this.opaqueTag = opaqueTag;
    }

    /**
     * Validates that the given String is a quoted String with an optional weak prefix.
     *
     * @param entityTag The String that should get validated.
     * @return True if the given String is valid. False if not.
     */
    public static boolean isValid(final String entityTag) {
        return entityTag.matches(Regex.ENTITY_TAG);
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
     * @param otherEntityTag The {@link EntityTag} to compare to.
     * @return True if this {@link EntityTag} matches the given other entity tag based on strong comparison.
     */
    public boolean strongCompareTo(final EntityTag otherEntityTag) {
        if (this.isWeak()) {
            return false;
        }

        if (otherEntityTag.isWeak()) {
            return false;
        }

        return this.equals(otherEntityTag);
    }

    /**
     * Implements weak comparison based on <a href="https://tools.ietf.org/html/rfc7232#section-2.3.2">RFC 7232</a>
     *
     * @param otherEntityTag The {@link EntityTag} to compare to.
     * @return True if this {@link EntityTag} matches the given other entity tag based on weak comparison.
     */
    public boolean weakCompareTo(final EntityTag otherEntityTag) {
        return this.getOpaqueTag().equals(otherEntityTag.getOpaqueTag());
    }

    /**
     * Builds an {@link EntityTag} from a String value.
     *
     * @param entityTag the string representation of the entity-tag.
     * @return The {@link EntityTag} built from the given string value.
     * @throws DittoHeaderInvalidException if the given {@code entityTag} is not valid according to
     * {@link #isValid(String)}.
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
     * Builds an {@link EntityTag} for the given {@code entity}.
     *
     * @param entity The entity for which an {@link EntityTag} should be built.
     * @return An optional for the {@link EntityTag} built from the given entity. Empty if no {@link EntityTag}
     * could be built for the given entity
     */
    public static Optional<EntityTag> fromEntity(final Object entity) {
        return EntityTagBuilder.buildFromEntity(entity);
    }

    /**
     * Creates a strong entity-tag with the given {@code opaqueTag}.
     *
     * @param opaqueTag The opaque-tag of the entity-tag.
     * @return A strong entity-tag with the given {@code opaqueTag}.
     */
    public static EntityTag strong(final String opaqueTag) {
        return new EntityTag(false, opaqueTag);
    }

    /**
     * Creates a weak entity-tag with the given {@code opaqueTag}.
     *
     * @param opaqueTag The opaque-tag of the entity-tag.
     * @return A weak entity-tag with the given {@code opaqueTag}.
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
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final EntityTag entityTag = (EntityTag) o;
        return weak == entityTag.weak &&
                Objects.equals(opaqueTag, entityTag.opaqueTag);
    }

    @Override
    public int hashCode() {
        return Objects.hash(weak, opaqueTag);
    }

    private static class Regex {

        private static final String WEAK_PREFIX = "(W/)";
        private static final String OPAQUE_TAG = "(\"[^\"*]*\")";
        private static final String ENTITY_TAG = WEAK_PREFIX + "?" + OPAQUE_TAG;
    }
}
