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
package org.eclipse.ditto.services.utils.headers.conditional;

import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.entitytag.EntityTag;
import org.eclipse.ditto.model.base.headers.entitytag.EntityTags;

public class IfNoneMatchPreconditionHeader implements PreconditionHeader<EntityTag> {

    private static final String IF_NONE_MATCH_HEADER_KEY = DittoHeaderDefinition.IF_NONE_MATCH.getKey();
    private final EntityTags entityTagsToMatch;

    private IfNoneMatchPreconditionHeader(final EntityTags entityTagsToMatch) {
        this.entityTagsToMatch = entityTagsToMatch;
    }

    @Override
    public String getKey() {
        return IF_NONE_MATCH_HEADER_KEY;
    }

    @Override
    public String getValue() {
        return entityTagsToMatch.toString();
    }

    /**
     * Indicates whether this {@link IfNoneMatchPreconditionHeader} meets the condition for the given {@code entityTag}
     * according to <a href="https://tools.ietf.org/html/rfc7232#section-3.2">RFC7232 - Section 3.1</a>.
     *
     * @param entityTag The entity tag for which this {@link IfNoneMatchPreconditionHeader} should meet the condition.
     * @return True if this {@link IfNoneMatchPreconditionHeader} meets the condition. False if not.
     */
    @Override
    public boolean meetsConditionFor(@Nullable final EntityTag entityTag) {

        if (entityTag == null) {
            return true;
        }

        return entityTagsToMatch.stream().allMatch(entityTagToMatch -> checkEntityTag(entityTagToMatch, entityTag));
    }

    private boolean checkEntityTag(final EntityTag entityTagToMatch, final EntityTag currentEntityTag) {
        if (entityTagToMatch.isAsterisk()) {
            // False because we checked that eTagValueOfEntity is null before => there is a representation for
            // the entity.
            return false;
        }

        return !currentEntityTag.weakCompareTo(entityTagToMatch);
    }

    /**
     * Extracts an {@link IfNoneMatchPreconditionHeader} from the given {@code dittoHeaders} if present.
     *
     * @param dittoHeaders The ditto headers that could contain an {@link IfNoneMatchPreconditionHeader}
     * @return Optional of {@link IfNoneMatchPreconditionHeader}. Empty if the given {@code dittoHeaders} don't contain
     * an {@link IfNoneMatchPreconditionHeader}.
     */
    public static Optional<IfNoneMatchPreconditionHeader> fromDittoHeaders(final DittoHeaders dittoHeaders) {
        return dittoHeaders.getIfNoneMatch().map(IfNoneMatchPreconditionHeader::new);
    }
}
